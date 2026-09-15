package com.selfcare.config.service;

import com.selfcare.config.domain.TenantConfig;
import com.selfcare.config.repository.TenantConfigRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ConflictException;
import com.selfcare.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant configuration service — CRUD for tenant master records.
 *
 * Caches tenants in Redis (5 min TTL). On update, cache is invalidated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantConfigService {

    private final TenantConfigRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "selfcare:tenant:config:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    public List<TenantConfig> listAll() {
        return repository.findAll();
    }

    public List<TenantConfig> listByIndustry(String industry) {
        if (industry == null || industry.isBlank()) {
            return repository.findAll();
        }
        return repository.findByIndustry(industry.toUpperCase());
    }

    public TenantConfig get(String tenantId) {
        if (tenantId == null) {
            throw new NotFoundException("Tenant", null);
        }
        String key = CACHE_PREFIX + tenantId.toLowerCase();
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof TenantConfig tc) {
            return tc;
        }
        TenantConfig tc = repository.findByTenantId(tenantId.toLowerCase())
                .orElseThrow(() -> new NotFoundException("Tenant", tenantId));
        redisTemplate.opsForValue().set(key, tc, CACHE_TTL);
        return tc;
    }

    public Optional<TenantConfig> find(String tenantId) {
        if (tenantId == null) return Optional.empty();
        return repository.findByTenantId(tenantId.toLowerCase());
    }

    public TenantConfig create(TenantConfig tenant) {
        if (tenant.getTenantId() == null || tenant.getTenantId().isBlank()) {
            tenant.setTenantId(UUID.randomUUID().toString());
        }
        tenant.setTenantId(tenant.getTenantId().toLowerCase());
        if (repository.existsByTenantId(tenant.getTenantId())) {
            throw new ConflictException("DUPLICATE_TENANT", "Tenant already exists: " + tenant.getTenantId());
        }
        Instant now = Instant.now();
        if (tenant.getCreatedAt() == null) tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        if (tenant.getStatus() == null) tenant.setStatus("ACTIVE");
        TenantConfig saved = repository.save(tenant);
        invalidate(saved.getTenantId());
        log.info("Tenant created: tenantId={}, industry={}, country={}",
                saved.getTenantId(), saved.getIndustry(), saved.getCountry());
        return saved;
    }

    public TenantConfig update(String tenantId, TenantConfig updates) {
        TenantConfig existing = get(tenantId);
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getIndustry() != null) existing.setIndustry(updates.getIndustry());
        if (updates.getCountry() != null) existing.setCountry(updates.getCountry());
        if (updates.getOperator() != null) existing.setOperator(updates.getOperator());
        if (updates.getSupportedLobs() != null) existing.setSupportedLobs(updates.getSupportedLobs());
        if (updates.getSupportedLocales() != null) existing.setSupportedLocales(updates.getSupportedLocales());
        if (updates.getDefaultLocale() != null) existing.setDefaultLocale(updates.getDefaultLocale());
        if (updates.getPackVersion() != null) existing.setPackVersion(updates.getPackVersion());
        if (updates.getProviderBindings() != null) existing.setProviderBindings(updates.getProviderBindings());
        if (updates.getEnabledFeatures() != null) existing.setEnabledFeatures(updates.getEnabledFeatures());
        if (updates.getEnvironments() != null) existing.setEnvironments(updates.getEnvironments());
        if (updates.getStatus() != null) existing.setStatus(updates.getStatus());
        existing.setUpdatedAt(Instant.now());
        existing.setUpdatedBy(TenantContext.get().getUserId());
        TenantConfig saved = repository.save(existing);
        invalidate(tenantId);
        log.info("Tenant updated: tenantId={}", tenantId);
        return saved;
    }

    public void delete(String tenantId) {
        repository.findByTenantId(tenantId.toLowerCase()).ifPresent(t -> {
            repository.delete(t);
            invalidate(tenantId);
            log.info("Tenant deleted: tenantId={}", tenantId);
        });
    }

    public void invalidate(String tenantId) {
        redisTemplate.delete(CACHE_PREFIX + tenantId.toLowerCase());
    }
}
