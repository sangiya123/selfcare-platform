package com.selfcare.admin.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Role-Based Access Control (RBAC) role definition.
 *
 * Maps a role name to a set of permissions.
 * Permissions follow a dotted naming convention:
 *   "journey:publish", "report:read", "user:manage", "config:approve", etc.
 *
 * <p>
 * This is a static configuration object — roles are not stored in the database.
 * For dynamic roles, see the studio RBAC editor.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRole {

    private String name;
    private String description;
    private Set<String> permissions;

    /**
     * Permission pattern - either a literal name or wildcard ("*", "journey:*").
     */
    public boolean hasPermission(String permission) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        if (permissions.contains("*")) {
            return true;  // super-user permission
        }
        if (permissions.contains(permission)) {
            return true;
        }
        // Wildcard match: "journey:*" matches "journey:publish"
        for (String granted : permissions) {
            if (granted.endsWith(":*")) {
                String prefix = granted.substring(0, granted.length() - 1);
                if (permission.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Built-in roles
    // -------------------------------------------------------------------------

    /**
     * SUPER_ADMIN: every permission.
     */
    public static AdminRole superAdmin() {
        return AdminRole.builder()
                .name("SUPER_ADMIN")
                .description("Platform-wide administrator with all permissions")
                .permissions(new HashSet<>(Arrays.asList("*")))
                .build();
    }

    /**
     * TENANT_ADMIN: full access within their tenant.
     */
    public static AdminRole tenantAdmin() {
        return AdminRole.builder()
                .name("TENANT_ADMIN")
                .description("Tenant administrator with full permissions within their tenant")
                .permissions(new HashSet<>(Arrays.asList(
                        "user:read", "user:create", "user:update", "user:delete",
                        "role:read",
                        "config:read", "config:create", "config:update", "config:approve", "config:publish",
                        "journey:read", "journey:create", "journey:update", "journey:publish", "journey:archive",
                        "report:read", "report:create", "report:update", "report:execute",
                        "audit:read", "audit:export"
                )))
                .build();
    }

    /**
     * EDITOR: content/journey editing.
     */
    public static AdminRole editor() {
        return AdminRole.builder()
                .name("EDITOR")
                .description("Can edit content, journeys, and configurations")
                .permissions(new HashSet<>(Arrays.asList(
                        "config:read", "config:create", "config:update",
                        "journey:read", "journey:create", "journey:update", "journey:publish",
                        "report:read", "report:execute",
                        "audit:read"
                )))
                .build();
    }

    /**
     * VIEWER: read-only.
     */
    public static AdminRole viewer() {
        return AdminRole.builder()
                .name("VIEWER")
                .description("Read-only access to the studio")
                .permissions(new HashSet<>(Arrays.asList(
                        "config:read",
                        "journey:read",
                        "report:read",
                        "audit:read"
                )))
                .build();
    }
}
