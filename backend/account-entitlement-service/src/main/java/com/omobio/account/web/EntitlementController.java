package com.omobio.account.web;

import com.omobio.account.service.EntitlementService;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import com.omobio.platform.common.web.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Entitlement REST API.
 *
 * Endpoints:
 *   GET /api/v1/entitlements/check?actor=&target=&action=
 *     - Returns 200 if the actor is authorized to perform action on target.
 *     - Returns 403 if not authorized.
 *
 * Used by other services to gate cross-connection operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/entitlements")
@RequiredArgsConstructor
@Tag(name = "Entitlements", description = "Cross-connection authorization checks")
public class EntitlementController {

    private final EntitlementService entitlementService;

    /**
     * Check whether an actor is authorized to perform an action on a target connection.
     *
     * @param actor  the account ID of the actor (e.g., the logged-in account)
     * @param target the connection ID being acted upon
     * @param action the action being attempted (e.g., PAY_BILL, RECHARGE)
     * @return 200 with authorized=true if allowed, 403 with authorized=false if not
     */
    @GetMapping("/check")
    @Operation(summary = "Check if an actor is authorized to perform an action on a target connection",
            description = "Returns 200 if authorized, 403 if not. The check follows ADR-006: " +
                    "the target must be in the actor's linked connection list.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> check(
            @RequestParam("actor") String actor,
            @RequestParam("target") String target,
            @RequestParam("action") String action) {

        String correlationId = TenantContext.get().getCorrelationId();
        log.debug("Entitlement check: actor={}, target={}, action={}, correlation={}",
                actor, target, action, correlationId);

        try {
            entitlementService.authorizeConnectionAction(actor, target, action);
            // Authorized
            return ResponseEntity.ok(ApiResponse.of(
                    Map.of(
                            "authorized", true,
                            "actor", actor,
                            "target", target,
                            "action", action
                    ),
                    correlationId
            ));
        } catch (ForbiddenException ex) {
            // Not authorized — return 403 with structured body (via ApiResponse)
            log.warn("Entitlement denied: actor={}, target={}, action={}, reason={}",
                    actor, target, action, ex.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.of(
                            Map.of(
                                    "authorized", false,
                                    "actor", actor,
                                    "target", target,
                                    "action", action,
                                    "reason", ex.getMessage()
                            ),
                            correlationId
                    ));
        }
    }
}
