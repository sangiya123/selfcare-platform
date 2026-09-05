package com.omobio.platform.common.adapter;

import java.time.Instant;


/**
 * Canonical interface for SIM lifecycle operations — activation, suspension,
 * deactivation, SIM swap, and port-in / port-out.
 * Telco-only. Implementations vary per operator BSS.
 *
 * @see com.omobio.account.service.SimLifecycleService
 */
public interface SimLifecycleProvider extends ApiAdapter {

    /**
     * Activate a new SIM card against a connection.
     *
     * @param tenantId   the operator tenant
     * @param iccid      SIM card ICCID
     * @param msisdn     normalised MSISDN to activate
     * @param planType   prepaid or postpaid
     * @param actParams  activation-specific parameters (e.g. SIM swap reason)
     * @return           activation result
     */
    SimActivationResult activate(
            String tenantId,
            String iccid,
            String msisdn,
            String planType,
            SimActParams actParams
    );

    /**
     * Suspend a connection (SIM temporarily barred — e.g. lost/stolen, non-payment).
     *
     * @param tenantId   the operator tenant
     * @param msisdn     normalised MSISDN
     * @param reason     suspension reason code
     * @return           suspension result
     */
    SimLifecycleResult suspend(String tenantId, String msisdn, String reason);

    /**
     * Resume a suspended connection.
     *
     * @param tenantId  the operator tenant
     * @param msisdn    normalised MSISDN
     * @return          resumption result
     */
    SimLifecycleResult resume(String tenantId, String msisdn);

    /**
     * Permanently deactivate a SIM / connection.
     *
     * @param tenantId  the operator tenant
     * @param msisdn    normalised MSISDN
     * @param reason    deactivation reason
     * @return          deactivation result
     */
    SimLifecycleResult deactivate(String tenantId, String msisdn, String reason);

    /**
     * Initiate a SIM swap (replace SIM while keeping the same MSISDN).
     *
     * @param tenantId   the operator tenant
     * @param oldIccid   current ICCID
     * @param newIccid   replacement ICCID
     * @param msisdn     normalised MSISDN
     * @param reason     swap reason: {@code lost}, {@code stolen}, {@code damaged}
     * @return           swap result
     */
    SimSwapResult swapSim(
            String tenantId,
            String oldIccid,
            String newIccid,
            String msisdn,
            String reason
    );

    /**
     * Get the current lifecycle status of a SIM / connection.
     *
     * @param tenantId  the operator tenant
     * @param msisdn    normalised MSISDN
     * @return          current SIM status
     */
    SimStatus getSimStatus(String tenantId, String msisdn);

    // ─── Inner types ─────────────────────────────────────────────────────

    enum SimState { ACTIVE, SUSPENDED, DEACTIVATED, PORTING_IN, PORTING_OUT, UNKNOWN }

    record SimActivationResult(
            boolean success,
            String msisdn,
            String iccid,
            SimState state,
            String activationDate,
            String failureReason
    ) {}

    record SimLifecycleResult(
            boolean success,
            String msisdn,
            SimState newState,
            String changedAt,
            String failureReason
    ) {}

    record SimSwapResult(
            boolean success,
            String msisdn,
            String oldIccid,
            String newIccid,
            String swappedAt,
            String failureReason
    ) {}

    record SimStatus(
            String msisdn,
            String iccid,
            SimState state,
            String planType,
            Instant lastActivity,
            Instant suspendedAt,
            Instant deactivatedAt
    ) {}

    record SimActParams(
            String referralCode,
            String idvRef,        // eKYC reference ID (null if not applicable)
            String dealerCode,
            String activationOffer
    ) {}
}
