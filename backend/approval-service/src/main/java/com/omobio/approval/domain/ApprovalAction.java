package com.omobio.approval.domain;

/**
 * The 9 high-risk actions requiring four-eyes approval.
 *
 * These are actions that could cause significant harm if performed without
 * review: production config changes, security-sensitive modifications,
 * customer data exposure, or privilege escalation.
 *
 * The set is fixed by business policy; it is NOT configurable per tenant
 * (unlike the per-tenant feature flag registry). This keeps the review
 * process auditable and prevents configuration drift.
 */
public enum ApprovalAction {

    /**
     * Production layout / navigation publish.
     * A published layout is immediately served to all users of the tenant.
     */
    LAYOUT_PUBLISH,

    /**
     * Authentication policy or token configuration change.
     * Affects how all users authenticate (e.g., token lifetime, MFA policy,
     * OIDC provider settings).
     */
    AUTH_POLICY_CHANGE,

    /**
     * Integration endpoint or credential rotation.
     * Changing an integration URL or rotating a secret affects real-time
     * system behaviour (SMSC, BSS, payment gateway, etc.).
     */
    INTEGRATION_CREDENTIAL_CHANGE,

    /**
     * Payment journey or rule configuration change.
     * Modifying payment rules, amounts, or journey steps can enable fraud.
     */
    PAYMENT_JOURNEY_CHANGE,

    /**
     * Feature flag rollout to 100% of users.
     * Enabling a feature for all users at once removes the safety of
     * staged rollouts.
     */
    FEATURE_ENABLE_100,

    /**
     * AI tool permission change.
     * Expanding AI tool permissions can expose sensitive operations to
     * end users via the AI copilot.
     */
    AI_TOOL_PERMISSION_CHANGE,

    /**
     * RAG knowledge source addition.
     * Adding a new knowledge source to the AI retrieval corpus can
     * inject unexpected content into AI responses.
     */
    RAG_SOURCE_ADDITION,

    /**
     * Report definition exposing customer-level data.
     * Reports with row-level customer data must be reviewed to ensure
     * PII handling and access controls are correct.
     */
    REPORT_CUSTOMER_DATA,

    /**
     * User or role privilege escalation.
     * Granting a higher-privilege role to a user or creating a role
     * with elevated permissions requires a security review.
     */
    ROLE_PRIVILEGE_ESCALATION
}
