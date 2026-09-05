package com.omobio.admin.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByEmail(String email);

    Optional<AdminUser> findByEmailAndStatus(String email, String status);

    List<AdminUser> findByTenantIdAndStatus(String tenantId, String status);

    List<AdminUser> findByStatus(String status);

    boolean existsByEmail(String email);
}
