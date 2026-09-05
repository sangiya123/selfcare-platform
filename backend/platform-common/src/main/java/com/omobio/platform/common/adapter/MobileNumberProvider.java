package com.omobio.platform.common.adapter;

import java.util.List;

/**
 * Canonical interface for MSISDN / mobile number operations.
 * Telco-only. Implementations vary per operator BSS (Dialog, Hutch, Airtel, etc.).
 *
 * <p>MSISDN is always masked in logs — never log the raw number.
 *
 * @see com.omobio.account.service.MobileNumberService
 */
public interface MobileNumberProvider extends ApiAdapter {

    /**
     * Validate whether an MSISDN is valid for the operator's numbering plan.
     *
     * @param tenantId  the operator tenant (e.g. {@code dialog-lk})
     * @param msisdn   raw MSISDN in international format (e.g. {@code 94771123456})
     * @return         validation result with normalised MSISDN
     */
    MsisdnValidationResult validate(String tenantId, String msisdn);

    /**
     * Check number portability (NP) status — whether the MSISDN has been
     * ported from another operator or is eligible for porting.
     *
     * @param tenantId  the operator tenant
     * @param msisdn   normalised MSISDN
     * @return         portability status
     */
    PortabilityStatus checkPortability(String tenantId, String msisdn);

    /**
     * Determine whether an MSISDN is prepaid or postpaid.
     *
     * @param tenantId  the operator tenant
     * @param msisdn   normalised MSISDN
     * @return         plan type
     */
    PlanType getPlanType(String tenantId, String msisdn);

    /**
     * Search for available MSISDNs in a specific number range or series.
     *
     * @param tenantId  the operator tenant
     * @param series    number prefix / range (e.g. {@code 94771}, {@code 9477x})
     * @param count     maximum results
     * @param planType  filter: {@code prepaid}, {@code postpaid}, or {@code any}
     * @return          available MSISDNs
     */
    List<String> searchAvailable(String tenantId, String series, int count, String planType);

    // ─── Inner types ─────────────────────────────────────────────────────

    record MsisdnValidationResult(
            boolean valid,
            String normalisedMsisdn,   // E.164 format: +94771...
            String formatError         // null if valid
    ) {}

    enum PortStatus {
        OWN_NETWORK,       // belongs to this operator
        PORTED_IN,         // ported from another operator
        PORTED_OUT,       // has left to another operator
        NOT_PORTABLE,      // not eligible for porting
        UNKNOWN
    }

    record PortabilityStatus(
            String msisdn,
            PortStatus status,
            String homeOperator,   // MNP — operator the number belongs to (null if not applicable)
            String portedAt         // ISO-8601, null if not ported
    ) {}

    enum PlanType { PREPAID, POSTPAID, HYBRID, UNKNOWN }
}
