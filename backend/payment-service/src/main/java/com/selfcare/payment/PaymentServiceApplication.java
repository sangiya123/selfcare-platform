package com.selfcare.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Payment Service — Payments, recharge, transactions.
 *
 * Capabilities:
 * - Own-connection recharge
 * - Cross-connection bill payment (with actor/target validation)
 * - Idempotency keys for all writes
 * - Transaction state machine: PENDING -> SUCCESS / FAILED / UNKNOWN / REVERSED
 * - Provider reconciliation
 * - Saved payment methods (tokenized, no raw PAN)
 * - Receipt generation
 * - Notification on completion
 *
 * Cross-connection payment (ADR-006):
 *   Validate: session valid, actor linked to account, target linked to account,
 *   target supports action, amount within policy, step-up auth if required,
 *   idempotency key valid and not already completed.
 */
@SpringBootApplication
@EnableScheduling
@EnableKafka
@ComponentScan(basePackages = {
    "com.selfcare"
})
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}