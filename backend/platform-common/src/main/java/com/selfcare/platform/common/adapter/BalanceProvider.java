package com.selfcare.platform.common.adapter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Provider interface for balance/usage queries.
 *
 * <p>Canonical home of the {@code BalanceProvider} contract so telco industry
 * packs (Dialog, Hutch, Airtel) can implement it without depending on a
 * service module.</p>
 *
 * <p>Each operator implements this to expose balance and allowance information
 * in canonical form.</p>
 */
public interface BalanceProvider extends ApiAdapter {

    /**
     * Fetch current balance for a connection.
     */
    Balance fetchBalance(String connectionId);

    /**
     * Fetch usage summary (data, voice, SMS) for a connection.
     * Period defaults to current billing cycle.
     */
    UsageSummary fetchUsage(String connectionId, Instant periodStart, Instant periodEnd);

    /**
     * Balance response.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class Balance {
        private String connectionId;
        private BigDecimal amount;
        private String currency;
        private String balanceType; // PREPAID, POSTPAID_DUE, POSTPAID_AVAILABLE
        private Instant expiryDate;
        private Instant timestamp;
        private Boolean isPrimary;
    }

    /**
     * Usage summary response.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class UsageSummary {
        private String connectionId;
        private Instant periodStart;
        private Instant periodEnd;
        private DataUsage data;
        private VoiceUsage voice;
        private SmsUsage sms;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class DataUsage {
        private Long totalBytes;       // Total data used
        private Long remainingBytes;   // Remaining from allowance
        private Long allowanceBytes;   // Total allowance
        private Instant resetDate;     // Next reset
        private Boolean isUnlimited;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class VoiceUsage {
        private Long totalSeconds;     // Total voice used
        private Long remainingSeconds; // Remaining
        private Long allowanceSeconds; // Total allowance
        private Instant resetDate;
        private Boolean isUnlimited;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class SmsUsage {
        private Long totalCount;       // Total SMS used
        private Long remainingCount;   // Remaining
        private Long allowanceCount;   // Total allowance
        private Instant resetDate;
        private Boolean isUnlimited;
    }
}