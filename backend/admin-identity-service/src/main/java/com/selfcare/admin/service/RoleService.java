package com.selfcare.admin.service;

import com.selfcare.admin.domain.AdminUser;
import com.selfcare.admin.domain.Role;
import com.selfcare.admin.repository.RoleRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Role-based access control.
 *
 * Permission format: <resource>:<action>
 *   e.g. layout:publish, theme:edit, integration:rotate, audit:view, role:manage
 *
 * Built-in roles:
 *   - tenant_admin: every permission (manage their tenant)
 *   - layout_editor: layout:*, theme:*
 *   - journey_editor: journey:*
 *   - integration_admin: integration:*
 *   - auditor: audit:view, *:view (read-only)
 *   - support: user:view, session:revoke (limited customer support)
 *   - viewer: *:view
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    /** Built-in permissions for built-in roles, applied on first deploy per tenant. */
    private static final Map<String, Set<String>> SYSTEM_ROLE_PERMS = Map.of(
        "tenant_admin", Set.of(
            "layout:publish", "layout:edit", "layout:view", "layout:rollback",
            "theme:edit", "theme:view",
            "journey:publish", "journey:edit", "journey:view",
            "integration:create", "integration:edit", "integration:rotate", "integration:view",
            "user:view", "user:edit", "user:disable",
            "session:revoke", "session:view",
            "audit:view",
            "role:manage",
            "featureflag:edit", "featureflag:view"
        ),
        "layout_editor", Set.of(
            "layout:edit", "layout:view", "layout:publish",
            "theme:edit", "theme:view"
        ),
        "journey_editor", Set.of(
            "journey:edit", "journey:view", "journey:publish"
        ),
        "integration_admin", Set.of(
            "integration:create", "integration:edit", "integration:rotate", "integration:view",
            "featureflag:edit", "featureflag:view"
        ),
        "auditor", Set.of(
            "audit:view", "layout:view", "theme:view", "journey:view",
            "integration:view", "user:view", "session:view"
        ),
        "support", Set.of(
            "user:view", "session:view", "session:revoke"
        ),
        "viewer", Set.of(
            "layout:view", "theme:view", "journey:view", "integration:view", "user:view"
        )
    );

    /** Seed built-in roles for a new tenant. Idempotent. */
    public void seedSystemRolesForTenant(String tenantId) {
        for (Map.Entry<String, Set<String>> entry : SYSTEM_ROLE_PERMS.entrySet()) {
            String name = entry.getKey();
            if (roleRepository.findByTenantIdAndName(tenantId, name).isEmpty()) {
                Role role = new Role();
                role.setTenantId(tenantId);
                role.setName(name);
                role.setDescription("System role: " + name);
                role.setSystemRole(true);
                role.setPermissions(new HashSet<>(entry.getValue()));
                roleRepository.save(role);
            }
        }
    }

    /** Resolve effective permission set for an admin user. */
    public Set<String> effectivePermissions(AdminUser user) {
        if (user == null || user.getRole() == null) return Set.of();
        Set<String> permissions = new HashSet<>();
        String roleName = user.getRole();
        roleRepository.findByTenantIdAndName(user.getTenantId(), roleName)
                .ifPresent(role -> permissions.addAll(role.getPermissions()));
        return permissions;
    }

    /** Check if the user has the given permission. */
    public boolean hasPermission(AdminUser user, String permission) {
        return effectivePermissions(user).contains(permission);
    }

    /** Check if the user has any of the given permissions. */
    public boolean hasAnyPermission(AdminUser user, String... permissions) {
        Set<String> effective = effectivePermissions(user);
        for (String p : permissions) {
            if (effective.contains(p)) return true;
        }
        return false;
    }

    public List<Role> listRoles(String tenantId) {
        return roleRepository.findByTenantId(tenantId);
    }
}
