package com.omobio.platform.common.adapter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Usage Provider contract for telco industry packs.
 *
 * Each operator (Dialog, Hutch, Airtel, ...) implements this interface to
 * expose detailed usage records for data, voice, and SMS used by the
 * connection-management service, usage-service, and BFFs.
 *
 * This interface complements {@link com.omobio.usage.adapter.BalanceProvider}
 * (which returns current balance and aggregate usage). UsageProvider returns
 * per-session / per-event usage records for detailed breakdowns.
 *
 * All methods take {@code tenantId} as the first argument.
 * Upstream config is loaded at runtime from {@link com.omobio.platform.common.tenant.TenantConfigurationService}.
 */
public interface UsageProvider extends ApiAdapter {

    /**
     * Get data usage records for a connection within a time window.
     *
     * @param tenantId the tenant identifier (e.g. {@code "dialog-lk"})
     * @param connectionId the connection ID (MSISDN)
     * @param from start of the period (inclusive)
     * @param to end of the period (exclusive)
     * @return list of data usage records, newest first
     */
    List<DataUsageRecord> getDataUsage(String tenantId, String connectionId,
                                       Instant from, Instant to);

    /**
     * Get voice call usage records for a connection.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @param from start of the period
     * @param to end of the period
     * @return list of voice usage records, newest first
     */
    List<VoiceUsageRecord> getVoiceUsage(String tenantId, String connectionId,
                                         Instant from, Instant to);

    /**
     * Get SMS usage records for a connection.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @param from start of the period
     * @param to end of the period
     * @return list of SMS usage records, newest first
     */
    List<SmsUsageRecord> getSmsUsage(String tenantId, String connectionId,
                                     Instant from, Instant to);

    /**
     * Get the current (real-time or near-real-time) usage counters.
     * Used for live dashboard display.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @return current usage snapshot
     */
    CurrentUsage getCurrentUsage(String tenantId, String connectionId);

    /**
     * Get roaming usage records when the subscriber is abroad.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @param from start of the period
     * @param to end of the period
     * @return list of roaming usage records
     */
    List<RoamingUsageRecord> getRoamingUsage(String tenantId, String connectionId,
                                              Instant from, Instant to);

    // ================================================================
    // Domain Objects
    // ================================================================

    /**
     * A single data session / usage record.
     */
    record DataUsageRecord(
            String recordId,
            String connectionId,
            Instant timestamp,
            Long bytesUsed,
            Long sessionDurationSeconds,
            String networkType,     // 5G, 4G, 3G, WiFi
            String sessionType,    // BACKGROUND, FOREGROUND, HOTSPOT
            String country,
            BigDecimal chargeableAmount,
            String currency
    ) {}

    /**
     * A single voice call record.
     */
    record VoiceUsageRecord(
            String recordId,
            String connectionId,
            Instant startTime,
            Instant endTime,
            Long durationSeconds,
            String direction,       // OUTGOING, INCOMING
            String calledNumber,
            String country,
            Boolean isRoaming,
            BigDecimal chargeableAmount,
            String currency
    ) {}

    /**
     * A single SMS record.
     */
    record SmsUsageRecord(
            String recordId,
            String connectionId,
            Instant timestamp,
            String direction,       // OUTGOING, INCOMING
            String recipientNumber,
            String country,
            Boolean isRoaming,
            BigDecimal chargeableAmount,
            String currency
    ) {}

    /**
     * Real-time (or near-real-time) usage snapshot for dashboard display.
     */
    record CurrentUsage(
            String connectionId,
            Instant snapshotTime,
            Long dataUsedBytes,
            Long dataAllowanceBytes,
            Long voiceUsedSeconds,
            Long voiceAllowanceSeconds,
            Integer smsUsed,
            Integer smsAllowance
    ) {}

    /**
     * A roaming usage record.
     */
    record RoamingUsageRecord(
            String recordId,
            String connectionId,
            Instant timestamp,
            String country,
            String networkOperator,
            String usageType,      // DATA, VOICE, SMS
            BigDecimal amount,
            BigDecimal charge,
            String currency
    ) {}
}
