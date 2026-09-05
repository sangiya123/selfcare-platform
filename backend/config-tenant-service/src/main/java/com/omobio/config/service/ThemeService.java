package com.omobio.config.service;

import com.omobio.config.domain.ThemeDocument;
import com.omobio.config.repository.ThemeDocumentRepository;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Theme service — manages theme documents (design tokens per tenant).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeService {

    private final ThemeDocumentRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "omobio:theme:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    public List<ThemeDocument> listForTenant(String tenantId) {
        return repository.findByTenantId(tenantId);
    }

    public ThemeDocument get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Theme", id));
    }

    public ThemeDocument getByRef(String tenantId, String name, String version) {
        return repository.findByTenantIdAndNameAndVersionAndStatus(tenantId, name, version, "PUBLISHED")
                .orElseThrow(() -> new NotFoundException("Theme", tenantId + "/" + name + "@" + version));
    }

    public ThemeDocument save(ThemeDocument theme) {
        if (theme.getId() == null) theme.setId(UUID.randomUUID().toString());
        Instant now = Instant.now();
        if (theme.getCreatedAt() == null) {
            theme.setCreatedAt(now);
            theme.setCreatedBy(TenantContext.get().getUserId());
        }
        theme.setUpdatedAt(now);
        if (theme.getStatus() == null) theme.setStatus("DRAFT");
        if (theme.getVersionNumber() == 0) theme.setVersionNumber(1);
        ThemeDocument saved = repository.save(theme);
        invalidateCache(saved);
        log.info("Theme saved: tenant={}, name={}, version={}",
                saved.getTenantId(), saved.getName(), saved.getVersion());
        return saved;
    }

    public ThemeDocument publish(String id) {
        ThemeDocument theme = get(id);
        theme.setStatus("PUBLISHED");
        theme.setPublishedAt(Instant.now());
        theme.setPublishedBy(TenantContext.get().getUserId());
        theme.setVersionNumber(theme.getVersionNumber() + 1);
        ThemeDocument saved = repository.save(theme);
        invalidateCache(saved);
        log.info("Theme published: tenant={}, name={}, versionNumber={}",
                saved.getTenantId(), saved.getName(), saved.getVersionNumber());
        return saved;
    }

    public void delete(String id) {
        ThemeDocument theme = get(id);
        repository.delete(theme);
        invalidateCache(theme);
    }

    private void invalidateCache(ThemeDocument theme) {
        redisTemplate.delete(CACHE_PREFIX + theme.getTenantId() + ":" + theme.getName());
    }
}
