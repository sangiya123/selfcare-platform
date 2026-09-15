package com.selfcare.usage.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Durable usage record — a snapshot of balance/usage for a connection.
 *
 * Persisted by the usage-service so we have:
 *  - last known balance/usage (cache fallback when provider is down),
 *  - sliding window of usage summaries for analytics / history view,
 *  - history visible in the dashboard even when the operator BSS is offline.
 *
 * Mirrors {@code com.selfcare.platform.common.adapter.BalanceProvider.{Balance, UsageSummary}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usage_records", indexes = {
    @Index(name = "ix_usage_entity_tenant_conn", columnList = "tenant_id, connection_id"),
    @Index(name = "ix_usage_entity_recorded", columnList = "recorded_at"),
    @Index(name = "ix_usage_entity_tenant_period", columnList = "tenant_id, period_start, period_end")
})
@EntityListeners(AuditingEntityListener.class)
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Long recordId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "connection_id", nullable = false, length = 64)
    private String connectionId;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    // --- Balance snapshot ---
    @Column(name = "balance_amount", precision = 19, scale = 4)
    private BigDecimal balanceAmount;

    @Column(name = "balance_currency", length = 8)
    private String balanceCurrency;

    @Column(name = "balance_type", length = 32)
    private String balanceType; // PREPAID | POSTPAID_DUE | POSTPAID_AVAILABLE

    @Column(name = "balance_expiry_at")
    private Instant balanceExpiryAt;

    @Column(name = "balance_is_primary")
    private Boolean balanceIsPrimary;

    // --- Data ---
    @Column(name = "data_total_bytes")
    private Long dataTotalBytes;

    @Column(name = "data_remaining_bytes")
    private Long dataRemainingBytes;

    @Column(name = "data_allowance_bytes")
    private Long dataAllowanceBytes;

    @Column(name = "data_reset_at")
    private Instant dataResetAt;

    @Column(name = "data_is_unlimited")
    private Boolean dataIsUnlimited;

    // --- Voice ---
    @Column(name = "voice_total_seconds")
    private Long voiceTotalSeconds;

    @Column(name = "voice_remaining_seconds")
    private Long voiceRemainingSeconds;

    @Column(name = "voice_allowance_seconds")
    private Long voiceAllowanceSeconds;

    @Column(name = "voice_reset_at")
    private Instant voiceResetAt;

    @Column(name = "voice_is_unlimited")
    private Boolean voiceIsUnlimited;

    // --- SMS ---
    @Column(name = "sms_total_count")
    private Long smsTotalCount;

    @Column(name = "sms_remaining_count")
    private Long smsRemainingCount;

    @Column(name = "sms_allowance_count")
    private Long smsAllowanceCount;

    @Column(name = "sms_reset_at")
    private Instant smsResetAt;

    @Column(name = "sms_is_unlimited")
    private Boolean smsIsUnlimited;

    @Column(name = "source_provider", length = 64)
    private String sourceProvider;

    @Column(name = "is_stale", nullable = false)
    @Builder.Default
    private Boolean isStale = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
