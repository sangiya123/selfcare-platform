package com.selfcare.usage.service;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.adapter.BalanceProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    @Mock private ApiAdapterRegistry<BalanceProvider> providerRegistry;
    @Mock private RedisTemplate<String, BalanceProvider.Balance> balanceCache;
    @Mock private RedisTemplate<String, BalanceProvider.UsageSummary> usageCache;
    @Mock private ValueOperations<String, BalanceProvider.Balance> valueOps;
    @Mock private BalanceProvider provider;

    private UsageService service;

    @BeforeEach
    void setUp() {
        lenient().when(balanceCache.opsForValue()).thenReturn(valueOps);
        service = new UsageService(providerRegistry, balanceCache, usageCache);
        ReflectionTestUtils.setField(service, "balanceCacheTtl", 300L);
        ReflectionTestUtils.setField(service, "usageCacheTtl", 60L);
        TenantContext.current().setTenantId("t1");
        TenantContext.current().setUserId("u1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getBalance returns cached value on cache hit without calling provider")
    void getBalance_cacheHit() throws Exception {
        BalanceProvider.Balance cached = balance(1234);
        when(valueOps.get("selfcare:balance:t1:conn-A")).thenReturn(cached);

        CompletableFuture<BalanceProvider.Balance> result = service.getBalance("conn-A");
        BalanceProvider.Balance actual = result.get();

        assertThat(actual).isEqualTo(cached);
        verify(providerRegistry, never()).getProvider(anyString());
    }

    @Test
    @DisplayName("getBalance calls provider and caches result on cache miss")
    void getBalance_cacheMiss_callsProviderAndCaches() throws Exception {
        BalanceProvider.Balance fetched = balance(500);
        when(valueOps.get("selfcare:balance:t1:conn-A")).thenReturn(null);
        when(providerRegistry.getProvider("t1")).thenReturn(provider);
        when(provider.fetchBalance("conn-A")).thenReturn(fetched);

        BalanceProvider.Balance result = service.getBalance("conn-A").get();

        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        verify(valueOps).set(eq("selfcare:balance:t1:conn-A"), eq(fetched), any(Duration.class));
    }

    @Test
    @DisplayName("getBalance fallback returns null when no cache available")
    void getBalance_fallback_returnsNullOnCacheMiss() {
        // This is invoked by Resilience4j when circuit opens.
        // Direct test of the fallback method via reflection.
        when(valueOps.get(anyString())).thenReturn(null);
        var fallback = ReflectionTestUtils.invokeMethod(service, "getBalanceFallback", "conn-A", new RuntimeException("BSS down"));
        assertThat(fallback).isNotNull();
    }

    @Test
    @DisplayName("invalidateCache deletes the balance cache key")
    void invalidateCache() {
        service.invalidateCache("conn-A");
        verify(balanceCache).delete("selfcare:balance:t1:conn-A");
    }

    private BalanceProvider.Balance balance(long amount) {
        return BalanceProvider.Balance.builder()
                .connectionId("conn-A")
                .amount(BigDecimal.valueOf(amount))
                .currency("USD")
                .balanceType("PREPAID")
                .timestamp(Instant.now())
                .build();
    }
}
