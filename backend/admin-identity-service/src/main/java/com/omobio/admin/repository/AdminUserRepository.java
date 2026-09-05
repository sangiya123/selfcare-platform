package com.omobio.admin.repository;

import com.omobio.admin.domain.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link AdminUser}.
 */
@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, String> {

    /**
     * Find a user by email within a tenant (or cross-tenant by email alone).
     */
    Optional<AdminUser> findByEmail(String email);

    /**
     * Find a user by tenant and email.
     */
    Optional<AdminUser> findByTenantIdAndEmail(String tenantId, String email);

    /**
     * Find all active users within a tenant.
     */
    List<AdminUser> findByTenantIdAndStatus(String tenantId, String status);

    /**
     * Find all active users (no tenant filter — for SUPER_ADMIN).
     */
    List<AdminUser> findByStatus(String status);

    /**
     * Check if an email is already in use within a tenant.
     */
    boolean existsByTenantIdAndEmail(String tenantId, String email);

    /**
     * Find all users by role.
     */
    List<AdminUser> findByRole(String role);
}
