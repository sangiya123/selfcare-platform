package com.selfcare.platform.common.contract.usage;

import java.time.Instant;
import java.util.List;

/**
 * Canonical usage/balance contract. Telco: balance/usage/allowance; insurance/other: the same
 * shapes re-map (claims balance, quota usage).
 */
public final class UsageContract {

    private UsageContract() {}

    public record BalanceItem(
            String type,
            String label,
            String amount,
            String unit,
            String bucket) {
    }

    public record BalanceResponse(
            List<BalanceItem> balances,
            Instant freshUntil) {
    }

    public record UsageRecord(
            Instant periodStart,
            Instant periodEnd,
            String category,
            String usedAmount,
            String allowance,
            String unit) {
    }

    public record UsageHistoryResponse(
            List<UsageRecord> items,
            long totalItems,
            int page,
            int size,
            boolean hasNext) {
    }

    public record Allowance(
            String name,
            String used,
            String total,
            String unit) {
    }

    public record UsageBundleResponse(
            List<Allowance> allowances,
            Instant expiresAt) {
    }

    public interface UsageService {
        BalanceResponse getBalance(String tenantId, String connectionId);

        UsageHistoryResponse getUsageHistory(String tenantId, String connectionId, int page, int size);

        UsageBundleResponse getBundles(String tenantId, String connectionId);
    }
}