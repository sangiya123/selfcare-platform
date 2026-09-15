-- V4__ai_evaluation_enhancements.sql
-- Selfcare Platform — ai-gateway
-- Adds multilingual quality, red-team cases, and per-use-case regression threshold
-- to AI evaluation. Per AI governance spec:
-- "Every AI use case ships with a versioned evaluation set, baseline score,
--  regression threshold and red-team cases."

-- ============================================================================
-- ai_evaluation_results — new columns
-- ============================================================================
ALTER TABLE ai_evaluation_results
    ADD COLUMN multilingual_quality DOUBLE NULL AFTER refusal_correctness,
    ADD COLUMN red_team_total INT NULL AFTER multilingual_quality,
    ADD COLUMN red_team_passed INT NULL AFTER red_team_total,
    ADD CONSTRAINT chk_eval_red_team_total
        CHECK (red_team_total IS NULL OR red_team_total >= 0),
    ADD CONSTRAINT chk_eval_red_team_passed
        CHECK (red_team_passed IS NULL
               OR (red_team_passed >= 0 AND red_team_passed <= red_team_total)),
    ADD CONSTRAINT chk_eval_multilingual
        CHECK (multilingual_quality IS NULL
               OR (multilingual_quality >= 0.0 AND multilingual_quality <= 1.0));

-- ============================================================================
-- ai_use_cases — per-use-case regression threshold
-- ============================================================================
ALTER TABLE ai_use_cases
    ADD COLUMN regression_threshold DOUBLE NOT NULL DEFAULT 0.05
        AFTER daily_budget_usd,
    ADD CONSTRAINT chk_use_case_regression_threshold
        CHECK (regression_threshold >= 0.0 AND regression_threshold <= 1.0);

-- ============================================================================
-- ai_evaluation_sets — versioned test-case management
-- ============================================================================
CREATE TABLE IF NOT EXISTS ai_evaluation_sets (
    evaluation_set_id     VARCHAR(64)   NOT NULL PRIMARY KEY,
    tenant_id             VARCHAR(32)   NULL, -- null = platform default
    use_case_id           VARCHAR(64)   NOT NULL,
    version               INT           NOT NULL,
    set_type              VARCHAR(16)   NOT NULL, -- BASELINE, REGRESSION, RED_TEAM, MULTILINGUAL
    total_cases           INT           NOT NULL,
    description           VARCHAR(512)  NULL,
    content_hash          VARCHAR(64)   NULL,
    is_active             BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by            VARCHAR(64)   NULL,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,

    INDEX ix_eval_set_tenant_usecase (tenant_id, use_case_id),
    INDEX ix_eval_set_use_case_version (use_case_id, version),

    CONSTRAINT uq_eval_set_usecase_version_type
        UNIQUE (tenant_id, use_case_id, version, set_type),
    CONSTRAINT chk_eval_set_type
        CHECK (set_type IN ('BASELINE', 'REGRESSION', 'RED_TEAM', 'MULTILINGUAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed default evaluation sets (BASELINE) for each default use case
INSERT INTO ai_evaluation_sets
    (evaluation_set_id, tenant_id, use_case_id, version, set_type,
     total_cases, description, is_active, created_by, created_at, updated_at)
VALUES
    ('eval-set:customer_chat:1', NULL, 'customer_chat', 1, 'BASELINE',
     50, 'Initial baseline for customer chat', TRUE,
     'platform-ai-owner', NOW(), NOW()),
    ('eval-set:customer_chat:rt:1', NULL, 'customer_chat', 1, 'RED_TEAM',
     20, 'Red-team cases for customer chat (prompt injection, jailbreak, exfiltration)',
     TRUE, 'platform-ai-owner', NOW(), NOW()),
    ('eval-set:support_summary:1', NULL, 'support_summary', 1, 'BASELINE',
     30, 'Initial baseline for support summary', TRUE,
     'platform-ai-owner', NOW(), NOW()),
    ('eval-set:recommendations:1', NULL, 'recommendations', 1, 'BASELINE',
     40, 'Initial baseline for recommendations', TRUE,
     'platform-ai-owner', NOW(), NOW()),
    ('eval-set:content_rewrite:1', NULL, 'content_rewrite', 1, 'BASELINE',
     25, 'Initial baseline for content rewrite', TRUE,
     'platform-ai-owner', NOW(), NOW()),
    ('eval-set:payment_action_prep:1', NULL, 'payment_action_prep', 1, 'BASELINE',
     30, 'Initial baseline for payment action prep (HIGH-risk)', TRUE,
     'platform-ai-owner', NOW(), NOW()),
    ('eval-set:claim_decision_support:1', NULL, 'claim_decision_support', 1, 'BASELINE',
     30, 'Initial baseline for claim decision support (HIGH-risk)', TRUE,
     'platform-ai-owner', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();
