package com.omobio.admin.repository;

import com.omobio.admin.domain.AuthSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for tenant-scoped auth & security settings.
 */
@Repository
public interface AuthSettingsRepository extends JpaRepository<AuthSettings, String> {

    /**
     * Find settings for a specific tenant.
     *
     * @param tenantId tenant ID
     * @return optional settings row
     */
    Optional<AuthSettings> findByTenantId(String tenantId);
}
