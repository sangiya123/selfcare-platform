package com.omobio.usage.service;

import com.omobio.platform.common.adapter.ApiAdapterRegistry;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ServiceUnavailableException;
import com.omobio.usage.adapter.BalanceProvider;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Usage service — queries balance/usage via tenant-specific providers.
 *
 * Implements:
 * - Caching in Redis with configurable TTL
 * - Circuit breaker per provider (resilience4j)
 * - Retry with exponential backoff
 * - Async time-limiter (operator SLA: 3 seconds)
 * - Graceful degradation: returns cached or fallback if provider fails
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageService {

    private final ApiAdapterRegistry<BalanceProvider> providerRegistry;
    private final RedisTemplate<String, BalanceProvider.Balance> balanceCache;
    private final RedisTemplate<String, BalanceProvider.UsageSummary> usageCache;

    @Value("${usage.balance.cache-ttl-seconds:${USAGE_BALANCE_CACHE_TTL:300}}")
    private long balanceCacheTtl;

    @Value("${usage.usage.cache-ttl-seconds:${USAGE_USAGE_CACHE_TTL:60}}")
    private long usageCacheTtl;

    private static final String BALANCE_CACHE_PREFIX = "omobio:balance:";
    private static final String USAGE_CACHE_PREFIX = "omobio:usage:";

    /**
     * Fetch current balance with caching, circuit breaker, and time limiter.
     */
    @CircuitBreaker(name = "usage.balance", fallbackMethod = "getBalanceFallback")
    @TimeLimiter(name = "usage.balance")
    @Retry(name = "usage.balance")
    public CompletableFuture<BalanceProvider.Balance> getBalance(String connectionId) {
        String tenantId = TenantContext.get().getTenantId();
        String cacheKey = BALANCE_CACHE_PREFIX + tenantId + ":" + connectionId;

        // Try cache first
        BalanceProvider.Balance cached = balanceCache.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Balance cache hit: connection={}", connectionId);
            return CompletableFuture.completedFuture(cached);
        }

        // Cache miss — call provider
        return CompletableFuture.supplyAsync(() -> {
            try {
                BalanceProvider provider = providerRegistry.getProvider(tenantId);
                BalanceProvider.Balance balance = provider.fetchBalance(connectionId);
                if (balance == null) {
                    throw new ServiceUnavailableException("BalanceProvider", "Null response", true);
                }
                // Cache the result
                balanceCache.opsForValue().set(cacheKey, balance, Duration.ofSeconds(balanceCacheTtl));
                return balance;
            } catch (Exception e) {
                log.error("Balance fetch failed for connection {}: {}", connectionId, e.getMessage());
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Circuit breaker fallback — returns last known balance or null.
     */
    @SuppressWarnings("unused")
    private CompletableFuture<BalanceProvider.Balance> getBalanceFallback(String connectionId, Throwable t) {
        log.warn("Balance fallback triggered for connection {}: {}", connectionId, t.getMessage());

        // Try to return last known balance
        String tenantId = TenantContext.get().getTenantId();
        String cacheKey = BALANCE_CACHE_PREFIX + tenantId + ":" + connectionId;
        BalanceProvider.Balance lastKnown = balanceCache.opsForValue().get(cacheKey);
        if (lastKnown != null) {
            // Mark as stale
            lastKnown.setTimestamp(Instant.EPOCH); // Indicates stale
            return CompletableFuture.completedFuture(lastKnown);
        }

        // No cache — return null (caller will handle)
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Fetch usage summary with caching.
     */
    @CircuitBreaker(name = "usage.summary", fallbackMethod = "getUsageFallback")
    @TimeLimiter(name = "usage.summary")
    @Retry(name = "usage.summary")
    public CompletableFuture<BalanceProvider.UsageSummary> getUsage(
            String connectionId, Instant periodStart, Instant periodEnd) {

        String tenantId = TenantContext.get().getTenantId();
        String cacheKey = USAGE_CACHE_PREFIX + tenantId + ":" + connectionId + ":"
                + periodStart.getEpochSecond() + ":" + periodEnd.getEpochSecond();

        BalanceProvider.UsageSummary cached = usageCache.opsForValue().get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                BalanceProvider provider = providerRegistry.getProvider(tenantId);
                BalanceProvider.UsageSummary usage = provider.fetchUsage(connectionId, periodStart, periodEnd);
                if (usage != null) {
                    usageCache.opsForValue().set(cacheKey, usage, Duration.ofSeconds(usageCacheTtl));
                }
                return usage;
            } catch (Exception e) {
                log.error("Usage fetch failed for connection {}: {}", connectionId, e.getMessage());
                throw new CompletionException(e);
            }
        });
    }

    @SuppressWarnings("unused")
    private CompletableFuture<BalanceProvider.UsageSummary> getUsageFallback(
            String connectionId, Instant periodStart, Instant periodEnd, Throwable t) {
        log.warn("Usage fallback triggered for connection {}: {}", connectionId, t.getMessage());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Invalidate cache for a connection (e.g., after recharge).
     */
    public void invalidateCache(String connectionId) {
        String tenantId = TenantContext.get().getTenantId();
        balanceCache.delete(BALANCE_CACHE_PREFIX + tenantId + ":" + connectionId);
        // Could also use SCAN to delete usage cache keys with prefix
    }
}