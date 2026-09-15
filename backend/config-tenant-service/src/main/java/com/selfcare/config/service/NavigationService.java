package com.selfcare.config.service;

import com.selfcare.config.domain.NavigationDocument;
import com.selfcare.config.repository.NavigationDocumentRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Navigation service — manages navigation documents (navigation graph per tenant).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationService {

    private final NavigationDocumentRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "selfcare:navigation:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    public List<NavigationDocument> listForTenant(String tenantId) {
        return repository.findByTenantId(tenantId);
    }

    public NavigationDocument get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Navigation", id));
    }

    public NavigationDocument getByRef(String tenantId, String name, int version, String status) {
        return repository.findByTenantIdAndNameAndVersionAndStatus(tenantId, name, version, status)
                .orElseThrow(() -> new NotFoundException("Navigation", tenantId + "/" + name + "@" + version + " (" + status + ")"));
    }

    public NavigationDocument getLatestPublished(String tenantId, String name) {
        return repository.findByTenantIdAndNameAndStatus(tenantId, name, "PUBLISHED")
                .or(() -> repository.findByTenantIdAndNameOrderByVersionDesc(tenantId, name).stream().findFirst())
                .orElseThrow(() -> new NotFoundException("Navigation", tenantId + "/" + name));
    }

    public NavigationDocument save(NavigationDocument nav) {
        if (nav.getId() == null) nav.setId(UUID.randomUUID().toString());
        Instant now = Instant.now();
        if (nav.getCreatedAt() == null) {
            nav.setCreatedAt(now);
            nav.setCreatedBy(TenantContext.get().getUserId());
        }
        nav.setUpdatedAt(now);
        if (nav.getStatus() == null) nav.setStatus("DRAFT");
        if (nav.getVersion() == 0) nav.setVersion(1);
        NavigationDocument saved = repository.save(nav);
        invalidateCache(saved);
        log.info("Navigation saved: tenant={}, name={}, version={}",
                saved.getTenantId(), saved.getName(), saved.getVersion());
        return saved;
    }

    public NavigationDocument publish(String id) {
        NavigationDocument nav = get(id);
        nav.setStatus("PUBLISHED");
        nav.setPublishedAt(Instant.now());
        nav.setPublishedBy(TenantContext.get().getUserId());
        nav.setVersion(nav.getVersion() + 1);
        NavigationDocument saved = repository.save(nav);
        invalidateCache(saved);
        log.info("Navigation published: tenant={}, name={}, version={}",
                saved.getTenantId(), saved.getName(), saved.getVersion());
        return saved;
    }

    public void delete(String id) {
        NavigationDocument nav = get(id);
        repository.delete(nav);
        invalidateCache(nav);
    }

    private void invalidateCache(NavigationDocument nav) {
        redisTemplate.delete(CACHE_PREFIX + nav.getTenantId() + ":" + nav.getName());
    }
}