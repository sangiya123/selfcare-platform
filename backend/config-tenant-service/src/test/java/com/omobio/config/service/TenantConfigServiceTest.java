package com.omobio.config.service;

import com.omobio.config.domain.TenantConfig;
import com.omobio.config.repository.TenantConfigRepository;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantConfigService.
 *
 * Verifies:
 * - get(tenantId) with Redis cache hit/miss
 * - listAll, listByIndustry
 * - create/update/delete with cache invalidation
 * - NotFoundException when tenant missing
 */
@ExtendWith(MockitoExtension.class)
class TenantConfigServiceTest {

    @Mock private TenantConfigRepository repository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    private TenantConfigService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new TenantConfigService(repository, redisTemplate);

        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        ctx.setUserId("admin-1");
        TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ======================================================================
    // get(tenantId) — cache hit
    // ======================================================================

    @Test
    @DisplayName("get returns cached tenant config on Redis cache hit")
    void get_cacheHit() {
        TenantConfig cached = TenantConfig.builder()
                .tenantId("dialog-lk")
                .name("Dialog")
                .industry("TELCO")
                .build();
        when(valueOps.get("omobio:tenant:config:dialog-lk")).thenReturn(cached);

        TenantConfig result = service.get("dialog-lk");

        assertThat(result.getTenantId()).isEqualTo("dialog-lk");
        assertThat(result.getName()).isEqualTo("Dialog");
        // No DB call
        verifyNoInteractions(repository);
    }

    // ======================================================================
    // get(tenantId) — cache miss
    // ======================================================================

    @Test
    @DisplayName("get queries DB on Redis cache miss and populates cache")
    void get_cacheMiss() {
        when(valueOps.get("omobio:tenant:config:dialog-lk")).thenReturn(null);

        TenantConfig tenant = TenantConfig.builder()
                .tenantId("dialog-lk")
                .name("Dialog")
                .industry("TELCO")
                .country("LK")
                .status("ACTIVE")
                .build();
        when(repository.findByTenantId("dialog-lk")).thenReturn(Optional.of(tenant));

        TenantConfig result = service.get("dialog-lk");

        assertThat(result.getTenantId()).isEqualTo("dialog-lk");
        assertThat(result.getName()).isEqualTo("Dialog");
        // Cache should be populated with 5-minute TTL
        verify(valueOps).set(eq("omobio:tenant:config:dialog-lk"), eq(tenant), any());
    }

    @Test
    @DisplayName("get throws NotFoundException when tenant not in DB")
    void get_notFound() {
        when(valueOps.get("omobio:tenant:config:does-not-exist")).thenReturn(null);
        when(repository.findByTenantId("does-not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("does-not-exist"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("get normalizes tenantId to lowercase for cache key")
    void get_lowercaseNormalize() {
        when(valueOps.get("omobio:tenant:config:dialog-lk")).thenReturn(null);
        TenantConfig tenant = TenantConfig.builder().tenantId("dialog-lk").build();
        when(repository.findByTenantId("dialog-lk")).thenReturn(Optional.of(tenant));

        service.get("DIALOG-LK"); // uppercase

        // Should use lowercase key
        verify(valueOps).get("omobio:tenant:config:dialog-lk");
    }

    // ======================================================================
    // listAll / listByIndustry
    // ======================================================================

    @Test
    @DisplayName("listAll returns all tenants")
    void listAll_all() {
        when(repository.findAll()).thenReturn(List.of(
                TenantConfig.builder().tenantId("t1").build(),
                TenantConfig.builder().tenantId("t2").build()));

        List<TenantConfig> result = service.listAll();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("listByIndustry filters by industry")
    void listByIndustry_filters() {
        when(repository.findByIndustry("TELCO")).thenReturn(List.of(
                TenantConfig.builder().tenantId("dialog-lk").industry("TELCO").build(),
                TenantConfig.builder().tenantId("hutch-lk").industry("TELCO").build()));

        List<TenantConfig> result = service.listByIndustry("telco");

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(t -> "TELCO".equals(t.getIndustry()));
    }

    @Test
    @DisplayName("listByIndustry normalizes to uppercase")
    void listByIndustry_uppercaseNormalize() {
        when(repository.findByIndustry("TELCO")).thenReturn(List.of());

        service.listByIndustry("telco");

        verify(repository).findByIndustry("TELCO");
    }

    // ======================================================================
    // create
    // ======================================================================

    @Test
    @DisplayName("create saves and returns new tenant config")
    void create_success() {
        TenantConfig input = TenantConfig.builder()
                .tenantId("new-tenant")
                .name("New Tenant")
                .industry("TELCO")
                .country("LK")
                .build();
        when(repository.save(any(TenantConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantConfig result = service.create(input);

        assertThat(result.getTenantId()).isEqualTo("new-tenant");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("create throws ConflictException for duplicate tenantId")
    void create_conflict() {
        TenantConfig input = TenantConfig.builder()
                .tenantId("dialog-lk")
                .name("Duplicate")
                .build();
        when(repository.existsByTenantId("dialog-lk")).thenReturn(true);

        assertThatThrownBy(() -> service.create(input))
                .isInstanceOf(com.omobio.platform.common.web.ConflictException.class);
    }

    // ======================================================================
    // update
    // ======================================================================

    @Test
    @DisplayName("update modifies and invalidates cache")
    void update_invalidatesCache() {
        TenantConfig existing = TenantConfig.builder()
                .tenantId("dialog-lk")
                .name("Dialog")
                .status("ACTIVE")
                .build();
        when(repository.findByTenantId("dialog-lk")).thenReturn(Optional.of(existing));
        when(repository.save(any(TenantConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantConfig updates = TenantConfig.builder()
                .name("Dialog Sri Lanka")
                .industry("TELCO")
                .build();
        service.update("dialog-lk", updates);

        // Cache should be invalidated
        verify(redisTemplate).delete("omobio:tenant:config:dialog-lk");
    }

    // ======================================================================
    // delete
    // ======================================================================

    @Test
    @DisplayName("delete removes tenant and invalidates cache")
    void delete_invalidatesCache() {
        TenantConfig existing = TenantConfig.builder()
                .tenantId("stale-tenant")
                .status("ACTIVE")
                .build();
        when(repository.findByTenantId("stale-tenant")).thenReturn(Optional.of(existing));

        service.delete("stale-tenant");

        verify(repository).delete(existing);
        verify(redisTemplate).delete("omobio:tenant:config:stale-tenant");
    }
}
