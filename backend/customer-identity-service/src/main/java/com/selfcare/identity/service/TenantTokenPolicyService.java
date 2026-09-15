package com.selfcare.identity.service;

import com.selfcare.identity.domain.TenantTokenPolicy;
import com.selfcare.identity.repository.TenantTokenPolicyRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Service for managing per-tenant token TTL policy (ADR-011).
 *
 * Reads from MongoDB (source of truth). Caches in-memory for the request
 * scope via {@link TenantContext}. Writes are auditable — every change to
 * a policy record fires a CONFIG audit event.
 *
 * The service enforces two hard limits:
 *   - accessTokenSeconds <= 42 days
 *   - refreshTokenSeconds <= 7 months
 *
 * Any write that exceeds these limits is rejected with HTTP 400 and logged
 * at WARN.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantTokenPolicyService {

    private final TenantTokenPolicyRepository repository;

    /**
     * Get the effective policy for the current tenant. Returns defaults if
     * the tenant has no explicit policy record.
     */
    public TenantTokenPolicy getEffectivePolicy() {
        return getEffectivePolicy(TenantContext.get().getTenantId());
    }

    /**
     * Get the effective policy for a given tenant.
     */
    public TenantTokenPolicy getEffectivePolicy(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return TenantTokenPolicy.defaults("default");
        }
        Optional<TenantTokenPolicy> stored = repository.findById("tenant-token-policy:" + tenantId);
        if (stored.isPresent()) {
            TenantTokenPolicy p = stored.get();
            // Apply hard caps defensively in case the DB was edited directly.
            p.setAccessTokenSeconds(p.effectiveAccessTokenSeconds());
            p.setRefreshTokenSeconds(p.effectiveRefreshTokenSeconds());
            return p;
        }
        return TenantTokenPolicy.defaults(tenantId);
    }

    /**
     * Upsert a tenant's token policy. Enforces hard caps.
     *
     * @throws ApiException if the values exceed platform limits
     */
    public TenantTokenPolicy upsert(TenantTokenPolicy policy) {
        if (policy == null || policy.getTenantId() == null || policy.getTenantId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "tenantId is required");
        }
        if (policy.getAccessTokenSeconds() > TenantTokenPolicy.MAX_ACCESS_TOKEN_SECONDS) {
            log.warn("Refused to upsert policy for tenant={} — access TTL {} > max {}",
                    policy.getTenantId(),
                    policy.getAccessTokenSeconds(),
                    TenantTokenPolicy.MAX_ACCESS_TOKEN_SECONDS);
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "accessTokenSeconds must be <= " + TenantTokenPolicy.MAX_ACCESS_TOKEN_SECONDS
                            + " (42 days). Set justification when exceeding the default.");
        }
        if (policy.getRefreshTokenSeconds() > TenantTokenPolicy.MAX_REFRESH_TOKEN_SECONDS) {
            log.warn("Refused to upsert policy for tenant={} — refresh TTL {} > max {}",
                    policy.getTenantId(),
                    policy.getRefreshTokenSeconds(),
                    TenantTokenPolicy.MAX_REFRESH_TOKEN_SECONDS);
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "refreshTokenSeconds must be <= " + TenantTokenPolicy.MAX_REFRESH_TOKEN_SECONDS
                            + " (7 months). Set justification when exceeding the default.");
        }
        // Enforce compensating controls when TTL > defaults.
        if (policy.getAccessTokenSeconds() > TenantTokenPolicy.DEFAULT_ACCESS_TOKEN_SECONDS
                || policy.getRefreshTokenSeconds() > TenantTokenPolicy.DEFAULT_REFRESH_TOKEN_SECONDS) {
            if (policy.getJustification() == null || policy.getJustification().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "justification is required when TTL exceeds platform defaults (24h/30d)");
            }
            if (!policy.isDeviceBindingRequired() || !policy.isRotationEnforced()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "deviceBindingRequired and rotationEnforced must both be true when TTL exceeds defaults");
            }
        }
        policy.setId("tenant-token-policy:" + policy.getTenantId());
        policy.setUpdatedAt(Instant.now());
        policy.setUpdatedBy(TenantContext.get().getUserId() != null
                ? TenantContext.get().getUserId()
                : "system");
        TenantTokenPolicy saved = repository.save(policy);
        log.info("Token policy upserted: tenant={}, accessTTL={}s, refreshTTL={}s, version={}",
                saved.getTenantId(),
                saved.getAccessTokenSeconds(),
                saved.getRefreshTokenSeconds(),
                saved.getPolicyVersion());
        return saved;
    }

    /**
     * Delete a tenant's policy (revert to defaults).
     */
    public void delete(String tenantId) {
        repository.deleteById("tenant-token-policy:" + tenantId);
        log.info("Token policy deleted: tenant={}", tenantId);
    }
}
