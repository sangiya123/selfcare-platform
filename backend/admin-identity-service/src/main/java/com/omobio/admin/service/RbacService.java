package com.omobio.admin.service;

import com.omobio.admin.domain.AdminRole;
import com.omobio.admin.domain.AdminUser;
import com.omobio.admin.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Role-Based Access Control (RBAC) service.
 *
 * Provides {@link #hasPermission(String, String)} for permission checks.
 * Roles are statically defined (see {@link AdminRole}); permissions are checked
 * via wildcard patterns.
 *
 * @see AdminRole
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RbacService {

    private final AdminUserRepository userRepository;

    /** Cache of role definitions. */
    private static final Map<String, AdminRole> ROLES = Map.of(
            "SUPER_ADMIN", AdminRole.superAdmin(),
            "TENANT_ADMIN", AdminRole.tenantAdmin(),
            "EDITOR", AdminRole.editor(),
            "VIEWER", AdminRole.viewer()
    );

    /**
     * Check whether a user has a specific permission.
     *
     * @param userId      the user ID
     * @param permission  the permission to check (e.g. "journey:publish")
     * @return true if the user has the permission
     */
    public boolean hasPermission(String userId, String permission) {
        if (userId == null || permission == null) {
            return false;
        }
        Optional<AdminUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return false;
        }
        AdminUser user = userOpt.get();
        if (!AdminUser.Status.ACTIVE.name().equals(user.getStatus())) {
            return false;
        }

        AdminRole role = ROLES.get(user.getRole());
        if (role == null) {
            log.warn("User {} has unrecognized role: {}", userId, user.getRole());
            return false;
        }
        return role.hasPermission(permission);
    }

    /**
     * Get the role definition for a role name.
     */
    public Optional<AdminRole> getRole(String roleName) {
        return Optional.ofNullable(ROLES.get(roleName));
    }

    /**
     * Check whether a user is a SUPER_ADMIN.
     */
    public boolean isSuperAdmin(String userId) {
        return userRepository.findById(userId)
                .map(u -> "SUPER_ADMIN".equals(u.getRole()))
                .orElse(false);
    }

    /**
     * List all known roles.
     */
    public Map<String, AdminRole> listRoles() {
        return ROLES;
    }
}
