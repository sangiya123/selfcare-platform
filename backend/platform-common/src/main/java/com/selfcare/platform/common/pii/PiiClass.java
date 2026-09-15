package com.selfcare.platform.common.pii;

import java.util.EnumSet;
import java.util.Set;

/**
 * PII classification taxonomy for the selfcare platform.
 *
 * Each enum constant represents a distinct PII category.
 * Used by {@link PiiMaskingService} to determine masking strategy
 * and audit classification.
 *
 * @see PiiMaskingService
 * @see ADR-018: PII Classification &amp; Masking Strategy
 */
public enum PiiClass {

    EMAIL("Email address", Sensitivity.HIGH, MaskingStrategy.REPLACE_TAG),
    PHONE("Phone number", Sensitivity.HIGH, MaskingStrategy.PARTIAL),
    MSISDN("Mobile subscriber number (telco)", Sensitivity.HIGH, MaskingStrategy.PARTIAL),
    NATIONAL_ID("Government-issued ID", Sensitivity.CRITICAL, MaskingStrategy.HASH),
    PAYMENT_CARD("Credit/debit card number", Sensitivity.CRITICAL, MaskingStrategy.PARTIAL),
    POLICY_NUMBER("Insurance policy number", Sensitivity.HIGH, MaskingStrategy.REPLACE_TAG),
    ACCOUNT_NUMBER("Bank account number", Sensitivity.CRITICAL, MaskingStrategy.HASH),
    IP_ADDRESS("IP address", Sensitivity.MEDIUM, MaskingStrategy.REPLACE_TAG),
    JWT("JSON Web Token", Sensitivity.CRITICAL, MaskingStrategy.JWT),
    SECRET_KEY("API key / secret", Sensitivity.CRITICAL, MaskingStrategy.REPLACE_TAG),
    CUSTOMER_NAME("Customer full name", Sensitivity.MEDIUM, MaskingStrategy.PARTIAL),
    DATE_OF_BIRTH("Date of birth", Sensitivity.HIGH, MaskingStrategy.PARTIAL),
    ADDRESS("Physical address", Sensitivity.HIGH, MaskingStrategy.REPLACE_TAG),
    ;

    private final String description;
    private final Sensitivity sensitivity;
    private final MaskingStrategy defaultStrategy;

    PiiClass(String description, Sensitivity sensitivity, MaskingStrategy defaultStrategy) {
        this.description = description;
        this.sensitivity = sensitivity;
        this.defaultStrategy = defaultStrategy;
    }

    public String getDescription() {
        return description;
    }

    public Sensitivity getSensitivity() {
        return sensitivity;
    }

    public MaskingStrategy getDefaultStrategy() {
        return defaultStrategy;
    }

    /**
     * Sensitivity levels drive audit requirements.
     */
    public enum Sensitivity {
        LOW,    // audit log, no special consent
        MEDIUM, // audit log + access control
        HIGH,   // audit log + access control + consent record
        CRITICAL // all of above + encryption at rest + tokenization
    }

    /**
     * Masking strategies applied by {@link PiiMaskingService}.
     */
    public enum MaskingStrategy {
        /** Replace with &lt;TAG&gt; — fully redacted */
        REPLACE_TAG,
        /** Show last N characters only — partially visible */
        PARTIAL,
        /** One-way HMAC-SHA256 — not reversible, linkable across records */
        HASH,
        /** Treat as a structured token — redact header.payload.footer separately */
        JWT
    }

    /**
     * Return all HIGH + CRITICAL PII classes that require consent before processing.
     */
    public static Set<PiiClass> consentRequired() {
        return EnumSet.of(
            EMAIL, PHONE, MSISDN, NATIONAL_ID,
            PAYMENT_CARD, POLICY_NUMBER, ACCOUNT_NUMBER,
            JWT, SECRET_KEY
        );
    }
}
