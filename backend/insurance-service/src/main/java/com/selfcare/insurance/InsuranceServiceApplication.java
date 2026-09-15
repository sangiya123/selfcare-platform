package com.selfcare.insurance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Insurance selfcare Service — the BFF for the insurance industry pack.
 *
 * Provides the BFF layer for insurance features:
 * - Policy management (view, renew)
 * - Claims (file, track, upload)
 * - Beneficiaries (add, update, remove)
 * - Premium management (pay, auto-debit)
 *
 * All client-specific logic is delegated to the InsuranceProvider
 * adapter registry — this service is client-agnostic and works with
 * any insurance provider (AIA, Allianz, Prudential, ...) without
 * code changes.
 */
@SpringBootApplication(
        scanBasePackages = "com.selfcare"
)
public class InsuranceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsuranceServiceApplication.class, args);
    }
}
