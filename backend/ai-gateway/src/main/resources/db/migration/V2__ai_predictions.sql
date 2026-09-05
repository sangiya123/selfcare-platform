-- V2__ai_predictions.sql
-- OMOBIO Selfcare Platform — ai-gateway
-- Table: ai_predictions
--
-- Predictive ML read model. Async precomputed scores per the AI scope spec
-- (section 2): "Predictions should generally be asynchronous/precomputed
-- and served from a read model."

CREATE TABLE IF NOT EXISTS ai_predictions (
    prediction_id        VARCHAR(64)   NOT NULL PRIMARY KEY,

    tenant_id            VARCHAR(32)   NOT NULL,

    -- Subject: CONNECTION, USER, POLICY
    target_type          VARCHAR(32)   NOT NULL,
    target_id            VARCHAR(128)  NOT NULL,

    -- Type: CHURN_RISK, DATA_EXHAUSTION, BILL_SHOCK, PAYMENT_FAILURE,
    --       SERVICE_ISSUE, OFFER_CONVERSION, COMPLAINT_ESCALATION,
    --       FRAUD_RISK, CLAIM_DELAY
    prediction_type      VARCHAR(32)   NOT NULL,

    score                DOUBLE        NOT NULL,
    level                VARCHAR(16)   NULL,    -- LOW, MEDIUM, HIGH, CRITICAL

    features             TEXT          NULL,
    explanation          VARCHAR(1024) NULL,
    recommended_action   VARCHAR(64)   NULL,    -- REACH_OUT, SHOW_OFFER, NOTHING
    model_version        VARCHAR(32)   NULL,

    computed_at          DATETIME(6)   NOT NULL,
    expires_at           DATETIME(6)   NOT NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,

    INDEX ix_pred_tenant (tenant_id),
    INDEX ix_pred_tenant_target (tenant_id, target_type, target_id),
    INDEX ix_pred_tenant_type_computed (tenant_id, prediction_type, computed_at),
    INDEX ix_pred_expires (expires_at),

    CONSTRAINT chk_pred_score CHECK (score >= 0.0 AND score <= 1.0),
    CONSTRAINT chk_pred_level CHECK (level IS NULL OR level IN
        ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_pred_action CHECK (recommended_action IS NULL OR recommended_action IN
        ('REACH_OUT', 'SHOW_OFFER', 'NOTHING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
