-- V3__ai_governance.sql
-- Selfcare Platform — ai-gateway
-- Tables for AI governance (ADR-013): use cases, prompt versions, evaluation
-- results, kill switches. Implements the controls required by
-- Planning doc/06_ai/02_AI_Governance_Evaluation.md.

-- ============================================================================
-- ai_use_cases — registry of every AI-backed capability
-- ============================================================================
CREATE TABLE IF NOT EXISTS ai_use_cases (
    use_case_id              VARCHAR(64)   NOT NULL PRIMARY KEY,
    tenant_id                VARCHAR(32)   NULL, -- null = platform default
    name                     VARCHAR(128)  NOT NULL,
    description              VARCHAR(512)  NULL,
    owner                    VARCHAR(128)  NULL,

    risk_tier                VARCHAR(16)   NOT NULL,  -- LOW, MEDIUM, HIGH
    approved_providers       VARCHAR(256)  NOT NULL,  -- comma-separated
    default_model            VARCHAR(64)   NULL,
    allowed_tools            VARCHAR(512)  NULL,

    human_approval_required  BOOLEAN       NOT NULL DEFAULT FALSE,
    retention_days           INT           NOT NULL DEFAULT 30,
    daily_budget_usd         DECIMAL(19, 4) NOT NULL DEFAULT 0,
    max_tokens_per_call      INT           NOT NULL DEFAULT 4000,
    user_daily_token_limit   BIGINT        NOT NULL DEFAULT 0,

    data_residency           VARCHAR(8)    NOT NULL DEFAULT 'ANY',
    pii_mask_input           BOOLEAN       NOT NULL DEFAULT TRUE,
    pii_mask_output          BOOLEAN       NOT NULL DEFAULT TRUE,
    enabled                  BOOLEAN       NOT NULL DEFAULT TRUE,
    approval_reference       VARCHAR(128)  NULL,

    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,

    INDEX ix_use_case_tenant (tenant_id),
    INDEX ix_use_case_id (use_case_id),

    CONSTRAINT chk_use_case_risk_tier
        CHECK (risk_tier IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_use_case_retention
        CHECK (retention_days >= 1 AND retention_days <= 365),
    CONSTRAINT chk_use_case_budget
        CHECK (daily_budget_usd >= 0),
    CONSTRAINT chk_use_case_residency
        CHECK (data_residency IN ('IN', 'US', 'EU', 'ANY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- ai_prompt_versions — versioned prompt templates (immutable history)
-- ============================================================================
CREATE TABLE IF NOT EXISTS ai_prompt_versions (
    prompt_version_id        VARCHAR(64)   NOT NULL PRIMARY KEY,
    tenant_id                VARCHAR(32)   NULL,
    template_id              VARCHAR(64)   NOT NULL,
    version                  INT           NOT NULL,
    industry                 VARCHAR(32)   NULL,
    content_hash             VARCHAR(64)   NULL,
    change_notes             VARCHAR(1024) NULL,
    is_active                BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by               VARCHAR(64)   NULL,
    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,

    INDEX ix_pv_tenant_template (tenant_id, template_id),
    INDEX ix_pv_template_version (template_id, version),

    CONSTRAINT uq_pv_template_version UNIQUE (tenant_id, template_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- ai_evaluation_results — eval runs and regression detection
-- ============================================================================
CREATE TABLE IF NOT EXISTS ai_evaluation_results (
    evaluation_result_id     VARCHAR(64)   NOT NULL PRIMARY KEY,
    tenant_id                VARCHAR(32)   NULL,
    use_case_id              VARCHAR(64)   NOT NULL,
    evaluation_set_version   INT           NOT NULL,
    model                    VARCHAR(64)   NOT NULL,
    prompt_version           INT           NULL,
    total_cases              INT           NOT NULL,
    passed_cases             INT           NOT NULL,
    task_success_rate        DOUBLE        NOT NULL,
    factual_accuracy         DOUBLE        NULL,
    hallucination_rate       DOUBLE        NULL,
    tool_selection_accuracy  DOUBLE        NULL,
    policy_compliance        DOUBLE        NULL,
    injection_resistance     DOUBLE        NULL,
    refusal_correctness      DOUBLE        NULL,
    passed                   BOOLEAN       NOT NULL,
    notes                    VARCHAR(2048) NULL,
    run_at                   DATETIME(6)   NOT NULL,
    created_at               DATETIME(6)   NOT NULL,

    INDEX ix_eval_tenant_usecase (tenant_id, use_case_id),
    INDEX ix_eval_run (use_case_id, evaluation_set_version, run_at),

    CONSTRAINT chk_eval_task_success
        CHECK (task_success_rate >= 0.0 AND task_success_rate <= 1.0),
    CONSTRAINT chk_eval_hallucination
        CHECK (hallucination_rate IS NULL
               OR (hallucination_rate >= 0.0 AND hallucination_rate <= 1.0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- ai_kill_switches — emergency disable
-- ============================================================================
CREATE TABLE IF NOT EXISTS ai_kill_switches (
    kill_switch_id           VARCHAR(64)   NOT NULL PRIMARY KEY,
    tenant_id                VARCHAR(32)   NULL, -- null = all tenants
    use_case_id              VARCHAR(64)   NULL, -- null = all use cases
    scope                    VARCHAR(16)   NOT NULL, -- USE_CASE or PROVIDER
    provider                 VARCHAR(64)   NULL,
    active                   BOOLEAN       NOT NULL,
    reason                   VARCHAR(1024) NULL,
    activated_by             VARCHAR(64)   NULL,
    activated_at             DATETIME(6)   NULL,
    expires_at               DATETIME(6)   NULL,
    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,

    INDEX ix_ks_tenant_usecase (tenant_id, use_case_id),
    INDEX ix_ks_active (active),

    CONSTRAINT chk_ks_scope
        CHECK (scope IN ('USE_CASE', 'PROVIDER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Seed default use cases (platform default — tenant_id NULL)
-- ============================================================================
INSERT INTO ai_use_cases
    (use_case_id, tenant_id, name, description, owner, risk_tier,
     approved_providers, default_model, allowed_tools, human_approval_required,
     retention_days, daily_budget_usd, max_tokens_per_call,
     data_residency, pii_mask_input, pii_mask_output, enabled, created_at, updated_at)
VALUES
    ('customer_chat', NULL, 'Customer Conversational Assistant',
     'Telco/insurance Q&A and troubleshooting',
     'platform-ai-owner', 'LOW', 'anthropic,openai', 'claude-sonnet-4-5',
     'getBalance,getUsage,getBills,searchFaq', FALSE,
     30, 100.0000, 4000, 'ANY', TRUE, TRUE, TRUE, NOW(), NOW()),
    ('support_summary', NULL, 'Support Conversation Summarization',
     'Structured summary of customer support chats',
     'platform-ai-owner', 'LOW', 'anthropic,openai', 'claude-sonnet-4-5',
     NULL, FALSE, 30, 50.0000, 2000, 'ANY', TRUE, TRUE, TRUE, NOW(), NOW()),
    ('recommendations', NULL, 'Offer/Product Recommendations',
     'Personalized offer ranking for the dashboard',
     'platform-ai-owner', 'MEDIUM', 'anthropic,openai', 'claude-sonnet-4-5',
     NULL, FALSE, 30, 50.0000, 2000, 'ANY', TRUE, TRUE, TRUE, NOW(), NOW()),
    ('content_rewrite', NULL, 'Content Rewrite / Translation',
     'AI-assisted content generation and translation for selfcare Studio',
     'platform-ai-owner', 'LOW', 'anthropic,openai', 'claude-sonnet-4-5',
     NULL, FALSE, 30, 50.0000, 4000, 'ANY', TRUE, TRUE, TRUE, NOW(), NOW()),
    ('payment_action_prep', NULL, 'Payment Action Preparation',
     'Prepare payment actions (NEVER executed without explicit user confirmation)',
     'platform-ai-owner', 'HIGH', 'anthropic', 'claude-sonnet-4-5',
     'preparePayment', TRUE,
     90, 200.0000, 2000, 'ANY', TRUE, TRUE, TRUE, NOW(), NOW()),
    ('claim_decision_support', NULL, 'Insurance Claim Decision Support',
     'Summary of evidence and recommended next step for human claims handler',
     'platform-ai-owner', 'HIGH', 'anthropic', 'claude-sonnet-4-5',
     'getPolicy,getClaimHistory', TRUE,
     90, 200.0000, 4000, 'ANY', TRUE, TRUE, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();
