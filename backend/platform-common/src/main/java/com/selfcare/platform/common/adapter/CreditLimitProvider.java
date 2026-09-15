package com.selfcare.platform.common.adapter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Provider interface for postpaid credit-limit queries.
 *
 * <p>Canonical home of the {@code CreditLimitProvider} contract so telco
 * industry packs (Dialog, Hutch, Airtel) can implement it without depending
 * on a service module.</p>
 *
 * <p>Each operator implements this to expose the currently available credit
 * for a postpaid connection (the amount of money the subscriber can still
 * spend on top of the bill already incurred).</p>
 */
public interface CreditLimitProvider extends ApiAdapter {

    /**
     * Fetch the current credit limit state for a connection.
     *
     * @param connectionId the connection identifier (MSISDN, contract id, ...)
     * @return the credit-limit snapshot, or {@code null} when the operator
     *         cannot supply one (no config, subscriber not found, ...)
     */
    CreditLimit fetchCreditLimit(String connectionId);

    /**
     * Credit-limit snapshot for a postpaid connection.
     */
    record CreditLimit(
            String connectionId,
            BigDecimal limitAmount,
            BigDecimal usedAmount,
            BigDecimal availableAmount,
            String currency,
            boolean creditEnabled,
            Instant timestamp
    ) {}
}