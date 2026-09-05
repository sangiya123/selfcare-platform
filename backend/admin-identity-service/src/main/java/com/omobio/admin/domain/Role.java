package com.omobio.admin.domain;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Role definition — a bundle of permissions. Tenant-scoped.
 * Examples: "tenant_admin", "layout_editor", "theme_editor", "viewer", "ops"
 */
@Entity
@Table(name = "admin_role", indexes = {
    @Index(name = "idx_role_tenant_name", columnList = "tenantId,name", unique = true)
})
public class Role {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "admin_role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", length = 100)
    private Set<String> permissions = new HashSet<>();

    /** System roles cannot be edited/deleted */
    @Column(nullable = false)
    private boolean systemRole = false;

    @PrePersist
    public void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }
    public boolean isSystemRole() { return systemRole; }
    public void setSystemRole(boolean systemRole) { this.systemRole = systemRole; }
}
