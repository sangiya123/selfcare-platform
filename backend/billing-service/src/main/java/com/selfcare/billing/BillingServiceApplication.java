package com.selfcare.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Billing Service — Bills, invoices, documents, late fees.
 *
 * Provides:
 * - Bill summary and detail with itemized line items
 * - Invoice statement generation (text/PDF)
 * - Late fee calculation and application (tenant-configurable)
 * - Automatic overdue promotion and daily late fee processing
 * - Category-based spending analytics (for AI recommendations)
 *
 * Cross-connection authorization (ADR-006): Pay for an allowed linked target connection.
 * Validation: account/connection relationship, bill ownership, amount, status, business rules.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.selfcare.billing",
    "com.selfcare.platform.common"
})
@EnableJpaRepositories(basePackages = "com.selfcare.billing.repository")
@EnableJpaAuditing
@EnableScheduling
public class BillingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApplication.class, args);
    }
}
