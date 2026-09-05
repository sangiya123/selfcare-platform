package com.omobio.admin.web;

import com.omobio.admin.domain.AdminUser;
import com.omobio.admin.repository.AdminUserRepository;
import com.omobio.admin.security.AdminJwtIssuer;
import com.omobio.admin.security.AdminPasswordEncoder;
import com.omobio.admin.service.ApprovalClient;
import com.omobio.admin.service.RbacService;
import com.omobio.platform.common.security.JwtService;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import com.omobio.platform.common.web.BadRequestException;
import com.omobio.platform.common.web.ConflictException;
import com.omobio.platform.common.web.ForbiddenException;
import com.omobio.platform.common.web.NotFoundException;
import com.omobio.platform.common.web.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin user CRUD controller.
 *
 * Endpoints for managing admin users. Only SUPER_ADMIN or TENANT_ADMIN can access.
 * TENANT_ADMIN is restricted to managing users within their own tenant.
 *
 * Four-eyes approval (ROLE_PRIVILEGE_ESCALATION):
 *   Changing a user's role to a higher-privilege role requires four-eyes approval.
 *   The update is gated through the approval-service.
 *
 * @see AdminUser
 * @see RbacService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserRepository userRepository;
    private final AdminPasswordEncoder passwordEncoder;
    private final AdminJwtIssuer jwtIssuer;
    private final RbacService rbacService;
    private final ApprovalClient approvalClient;

    /** Roles considered higher-privilege than standard user roles. */
    private static final Map<String, Integer> ROLE_HIERARCHY = Map.of(
            "VIEWER", 1,
            "EDITOR", 2,
            "TENANT_ADMIN", 3,
            "SUPER_ADMIN", 4
    );

    private static final Duration APPROVAL_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    /**
     * List admin users.
     *
     * SUPER_ADMIN sees all users. TENANT_ADMIN sees only their tenant.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminUser>>>
    listUsers(@RequestHeader("Authorization") String authorization) {
        JwtService.JwtClaims claims = extractClaims(authorization);
        String callingUserId = claims.getSubject();

        if (!rbacService.isSuperAdmin(callingUserId)) {
            // TENANT_ADMIN — only list users within their tenant
            List<AdminUser> users = userRepository.findByTenantIdAndStatus(
                    claims.getTenantId(), AdminUser.Status.ACTIVE.name());
            return ResponseEntity.ok(ApiResponse.of(users));
        } else {
            List<AdminUser> users = userRepository.findByStatus(AdminUser.Status.ACTIVE.name());
            return ResponseEntity.ok(ApiResponse.of(users));
        }
    }

    /**
     * Get a specific admin user by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUser>>
    getUser(@RequestHeader("Authorization") String authorization, @PathVariable String id) {
        JwtService.JwtClaims claims = extractClaims(authorization);
        requirePermission(claims.getSubject(), "user:read");

        AdminUser user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("AdminUser", id));

        checkTenantScope(claims, user);
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    /**
     * Create a new admin user.
     *
     * Creating a user with a higher-privilege role (TENANT_ADMIN, SUPER_ADMIN)
     * requires four-eyes approval.
     *
     * Requires user:create permission.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AdminUser>>
    createUser(@RequestHeader("Authorization") String authorization,
               @RequestBody CreateUserRequest request) {
        JwtService.JwtClaims claims = extractClaims(authorization);
        requirePermission(claims.getSubject(), "user:create");

        String tenantId = claims.getTenantId();
        if (rbacService.isSuperAdmin(claims.getSubject())) {
            tenantId = request.tenantId() != null ? request.tenantId() : tenantId;
        }

        if (userRepository.existsByTenantIdAndEmail(tenantId, request.email())) {
            throw new BadRequestException("Email already in use: " + request.email());
        }

        String role = request.role() != null ? request.role() : "TENANT_ADMIN";

        // Check for privilege escalation
        if (isPrivilegeEscalation(claims.getSubject(), role)) {
            return handlePrivilegeEscalation(authorization, tenantId, claims, role, request, true);
        }

        AdminUser user = AdminUser.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .email(request.email())
                .fullName(request.fullName())
                .role(role)
                .status(AdminUser.Status.PENDING_ACTIVATION.name())
                .passwordHash(passwordEncoder.hash(request.password()))
                .mfaEnabled(false)
                .createdBy(claims.getSubject())
                .build();

        user = userRepository.save(user);
        log.info("Admin user created: userId={}, tenantId={}, by={}, role={}",
                user.getId(), user.getTenantId(), claims.getSubject(), role);
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    /**
     * Update an admin user.
     *
     * Changing a user's role to a higher-privilege role requires four-eyes approval.
     *
     * Requires user:update permission.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUser>>
    updateUser(@RequestHeader("Authorization") String authorization,
               @PathVariable String id,
               @RequestBody UpdateUserRequest request) {
        JwtService.JwtClaims claims = extractClaims(authorization);
        requirePermission(claims.getSubject(), "user:update");

        AdminUser user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("AdminUser", id));
        checkTenantScope(claims, user);

        if (request.fullName() != null) user.setFullName(request.fullName());
        if (request.status() != null) user.setStatus(request.status());

        // Role change with privilege escalation check
        if (request.role() != null && !request.role().equals(user.getRole())) {
            if (isPrivilegeEscalation(claims.getSubject(), request.role())) {
                return handlePrivilegeEscalation(authorization, user.getTenantId(), claims,
                        request.role(), null, false);
            }
            user.setRole(request.role());
        }

        user = userRepository.save(user);
        log.info("Admin user updated: userId={}, by={}", id, claims.getSubject());
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    /**
     * Delete (disable) an admin user.
     *
     * Requires user:delete permission.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>>
    deleteUser(@RequestHeader("Authorization") String authorization,
               @PathVariable String id) {
        JwtService.JwtClaims claims = extractClaims(authorization);
        requirePermission(claims.getSubject(), "user:delete");

        AdminUser user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("AdminUser", id));
        checkTenantScope(claims, user);

        if (id.equals(claims.getSubject())) {
            throw new BadRequestException("Cannot delete your own account");
        }

        user.setStatus(AdminUser.Status.DELETED.name());
        userRepository.save(user);
        log.info("Admin user disabled: userId={}, by={}", id, claims.getSubject());
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    // -------------------------------------------------------------------------
    // Approval flow for privilege escalation
    // -------------------------------------------------------------------------

    /**
     * Determine if assigning a role constitutes a privilege escalation.
     *
     * Escalation = assigning a role that is higher in the hierarchy than the
     * caller's own role.
     */
    private boolean isPrivilegeEscalation(String callerId, String targetRole) {
        String callerRole = userRepository.findById(callerId)
                .map(AdminUser::getRole)
                .orElse(null);

        int callerLevel = callerRole != null
                ? ROLE_HIERARCHY.getOrDefault(callerRole, 0)
                : 0;
        int targetLevel = ROLE_HIERARCHY.getOrDefault(targetRole, 0);

        return targetLevel > callerLevel;
    }

    /**
     * Submit the privilege escalation for four-eyes approval.
     *
     * For create: immediately after approval, the user is created.
     * For update: immediately after approval, the role is changed.
     */
    private ResponseEntity<ApiResponse<AdminUser>> handlePrivilegeEscalation(
            String authorization,
            String tenantId,
            JwtService.JwtClaims claims,
            String targetRole,
            CreateUserRequest createRequest,
            boolean isCreate) {

        String callerRole = userRepository.findById(claims.getSubject())
                .map(AdminUser::getRole)
                .orElse("UNKNOWN");

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("userId", isCreate ? "NEW" : createRequest != null ? "PENDING" : "EXISTING");
        snapshot.put("targetRole", targetRole);
        snapshot.put("callerRole", callerRole);
        if (!isCreate) {
            snapshot.put("currentRole", callerRole);
        }

        String requesterId = claims.getSubject();
        String requesterEmail = claims.getSubject(); // Email not in claims — use ID

        String requestId = approvalClient.submitForApproval(
                        tenantId,
                        "ROLE_PRIVILEGE_ESCALATION",
                        "admin_user",
                        isCreate ? "new-user:" + createRequest.email() : claims.getSubject(),
                        requesterId,
                        requesterEmail,
                        snapshot)
                .block(APPROVAL_TIMEOUT);

        if (requestId == null) {
            throw new ConflictException("ROLE_PRIVILEGE_ESCALATION",
                    "Could not submit for approval — approval-service is unavailable");
        }

        log.info("Role escalation submitted for approval: requestId={}, targetRole={}",
                requestId, targetRole);

        // Block and wait for terminal decision
        String finalStatus = waitForDecision(requestId);
        if (!"APPROVED".equals(finalStatus)) {
            throw new ConflictException("ROLE_PRIVILEGE_ESCALATION",
                    "Role change was " + finalStatus + " — no change applied");
        }

        log.info("Role escalation approved: requestId={}, applying change", requestId);

        if (isCreate) {
            // Apply the user creation
            AdminUser user = AdminUser.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(tenantId)
                    .email(createRequest.email())
                    .fullName(createRequest.fullName())
                    .role(targetRole)
                    .status(AdminUser.Status.PENDING_ACTIVATION.name())
                    .passwordHash(passwordEncoder.hash(createRequest.password()))
                    .mfaEnabled(false)
                    .createdBy(claims.getSubject())
                    .build();
            user = userRepository.save(user);
            log.info("Admin user created after approval: userId={}, role={}",
                    user.getId(), targetRole);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.of(user));
        } else {
            // Apply the role update
            return ResponseEntity.ok(ApiResponse.of(userRepository.save(
                    userRepository.findById(claims.getSubject()).orElseThrow())));
        }
    }

    /**
     * Poll until the approval reaches a terminal state.
     */
    private String waitForDecision(String requestId) {
        Instant deadline = Instant.now().plus(APPROVAL_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            var status = approvalClient.getRequestStatus(requestId)
                    .block(Duration.ofSeconds(5));
            if (status != null && status.isPresent()) {
                String s = status.get();
                if ("APPROVED".equals(s) || "REJECTED".equals(s)
                        || "CANCELLED".equals(s) || "EXPIRED".equals(s)) {
                    return s;
                }
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "INTERRUPTED";
            }
        }
        return "TIMEOUT";
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void requirePermission(String userId, String permission) {
        if (!rbacService.hasPermission(userId, permission)) {
            throw new ForbiddenException("user:delete".equals(permission)
                    ? "Insufficient permission to delete users"
                    : "Insufficient permission: " + permission);
        }
    }

    private void checkTenantScope(JwtService.JwtClaims claims, AdminUser target) {
        if (rbacService.isSuperAdmin(claims.getSubject())) {
            return;  // SUPER_ADMIN can manage any user
        }
        if (!claims.getTenantId().equals(target.getTenantId())) {
            throw new ForbiddenException("user:read", target.getId());
        }
    }

    private JwtService.JwtClaims extractClaims(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        String token = authorization.substring(7);
        try {
            return jwtIssuer.validate(token);
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid token: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    public record CreateUserRequest(
            String email,
            String fullName,
            String password,
            String role,
            String tenantId
    ) {}

    public record UpdateUserRequest(
            String fullName,
            String role,
            String status
    ) {}
}
