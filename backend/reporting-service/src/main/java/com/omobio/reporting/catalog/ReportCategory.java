package com.omobio.reporting.catalog;

/**
 * Top-level grouping for reports in the catalog.
 *
 * Mirrors the 11 sections in
 * <em>Reports_Analytics_Scope.md</em> so the catalog can be browsed by area.
 */
public enum ReportCategory {
    /** Registrations, logins, DAU/WAU/MAU, session, language, platform, churn. */
    CUSTOMER_ADOPTION,
    /** Linked connections, add/remove events, denial, cache health. */
    CONNECTIONS,
    /** Page load, widget success, config version, feature-flag exposure. */
    DASHBOARD_EXPERIENCE,
    /** Catalog, product funnel, eligibility, promotion uptake, recommendations. */
    PRODUCTS_OFFERS,
    /** Usage views, bill views, reminders, selfcare task completion. */
    USAGE_BILLING,
    /** Payment attempts, method mix, idempotency, provider reconciliation. */
    PAYMENT_RECHARGE,
    /** Sent/delivered/read/click, opt-out, channel performance, A/B variants. */
    CAMPAIGN_NOTIFICATION,
    /** Service requests, NPS, FAQ, AI-to-human handoff. */
    SUPPORT_FEEDBACK,
    /** Request volume, latency, circuit-breaker, dependency availability. */
    API_PROVIDER_OPS,
    /** Admin actions, role changes, config publish/rollback, exports. */
    SECURITY_AUDIT,
    /** AI sessions, model cost, quality, tool calls, safety, RAG. */
    AI
}
