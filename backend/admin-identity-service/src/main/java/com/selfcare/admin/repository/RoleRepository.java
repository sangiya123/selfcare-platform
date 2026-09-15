package com.selfcare.admin.repository;

import com.selfcare.admin.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, String> {

    Optional<Role> findByTenantIdAndName(String tenantId, String name);

    List<Role> findByTenantId(String tenantId);
}
