package com.omobio.usage.web;

import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import com.omobio.usage.adapter.BalanceProvider.Balance;
import com.omobio.usage.adapter.BalanceProvider.UsageSummary;
import com.omobio.usage.domain.Allowance;
import com.omobio.usage.domain.UsageRecord;
import com.omobio.usage.service.AllowanceService;
import com.omobio.usage.service.UsageHistoryService;
import com.omobio.usage.service.UsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Usage REST API.
 *
 * Endpoints:
 *   GET  /api/v1/balance/{connectionId}                  — Current balance (live + cache)
 *   GET  /api/v1/usage/{connectionId}                   — Usage summary (live + cache)
 *   POST /api/v1/usage/cache/invalidate                 — Invalidate cache
 *   GET  /api/v1/allowances/{connectionId}              — Active allowances for a connection
 *   GET  /api/v1/allowances/{connectionId}/{id}         — Single allowance detail
 *   GET  /api/v1/usage/{connectionId}/history           — 30-day usage history
 *   GET  /api/v1/usage/{connectionId}/last-known        — Last persisted balance snapshot
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Usage", description = "Balance, usage, allowances, history")
public class UsageController {

    private final UsageService usageService;
    private final AllowanceService allowanceService;
    private final UsageHistoryService usageHistoryService;

    @GetMapping("/balance/{connectionId}")
    @Operation(summary = "Get current balance", description = "Returns the current balance for a connection")
    public CompletableFuture<ResponseEntity<ApiResponse<Balance>>> getBalance(@PathVariable String connectionId) {
        log.debug("Balance request: connection={}, tenant={}", connectionId, TenantContext.get().getTenantId());
        return usageService.getBalance(connectionId)
                .thenApply(balance -> ResponseEntity.ok(ApiResponse.of(balance, TenantContext.get().getCorrelationId())));
    }

    @GetMapping("/usage/{connectionId}")
    @Operation(summary = "Get usage summary", description = "Returns usage for a connection over a period")
    public CompletableFuture<ResponseEntity<ApiResponse<UsageSummary>>> getUsage(
            @PathVariable String connectionId,
            @RequestParam(required = false) Long fromEpoch,
            @RequestParam(required = false) Long toEpoch) {

        Instant now = Instant.now();
        Instant periodStart = fromEpoch != null ? Instant.ofEpochSecond(fromEpoch) : now.minusSeconds(30 * 86400);
        Instant periodEnd = toEpoch != null ? Instant.ofEpochSecond(toEpoch) : now;

        return usageService.getUsage(connectionId, periodStart, periodEnd)
                .thenApply(usage -> ResponseEntity.ok(ApiResponse.of(usage, TenantContext.get().getCorrelationId())));
    }

    @PostMapping("/usage/cache/invalidate")
    @Operation(summary = "Invalidate cache", description = "Invalidate cached balance/usage for a connection")
    public ResponseEntity<ApiResponse<Void>> invalidateCache(@RequestParam String connectionId) {
        usageService.invalidateCache(connectionId);
        return ResponseEntity.ok(ApiResponse.of(null, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/allowances/{connectionId}")
    @Operation(summary = "List active allowances", description = "Returns active allowances for a connection")
    public ResponseEntity<ApiResponse<List<Allowance>>> listAllowances(@PathVariable String connectionId) {
        return ResponseEntity.ok(ApiResponse.of(
                allowanceService.getActiveAllowances(connectionId),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/allowances/{connectionId}/{allowanceId}")
    @Operation(summary = "Get allowance detail", description = "Returns a single allowance by id")
    public ResponseEntity<ApiResponse<Allowance>> getAllowance(
            @PathVariable String connectionId,
            @PathVariable String allowanceId) {
        return ResponseEntity.ok(ApiResponse.of(
                allowanceService.getAllowance(connectionId, allowanceId),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/usage/{connectionId}/history")
    @Operation(summary = "Get usage history", description = "Returns last N days of usage records for a connection")
    public ResponseEntity<ApiResponse<List<UsageRecord>>> getHistory(
            @PathVariable String connectionId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.of(
                usageHistoryService.recentHistory(connectionId, days),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/usage/{connectionId}/last-known")
    @Operation(summary = "Get last known balance snapshot", description = "Returns the last persisted balance — works even when the operator BSS is down")
    public ResponseEntity<ApiResponse<UsageRecord>> getLastKnown(@PathVariable String connectionId) {
        return ResponseEntity.ok(ApiResponse.of(
                usageHistoryService.lastKnown(connectionId),
                TenantContext.get().getCorrelationId()));
    }
}
