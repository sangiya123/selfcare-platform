-- V1__create_report_tables.sql

CREATE TABLE IF NOT EXISTS report_definitions (
    id                  VARCHAR(64) NOT NULL,
    tenant_id           VARCHAR(32) NOT NULL,
    name                VARCHAR(128) NOT NULL,
    description         VARCHAR(512),
    query              MEDIUMTEXT NOT NULL,
    parameters_json    MEDIUMTEXT,
    schedule_cron      VARCHAR(64),
    recipients          VARCHAR(1024),
    output_format      VARCHAR(16) NOT NULL,
    status             VARCHAR(16) NOT NULL,
    created_by         VARCHAR(64),
    updated_by         VARCHAR(64),
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE INDEX ix_report_def_tenant_name (tenant_id, name),
    INDEX ix_report_def_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS report_executions (
    id                  VARCHAR(64) NOT NULL,
    tenant_id           VARCHAR(32) NOT NULL,
    report_id           VARCHAR(64) NOT NULL,
    status              VARCHAR(16) NOT NULL,
    started_at          DATETIME(6) NOT NULL,
    completed_at        DATETIME(6),
    parameters_json     MEDIUMTEXT,
    row_count           BIGINT,
    result_url          VARCHAR(1024),
    result_size_bytes  BIGINT,
    error              VARCHAR(2048),
    triggered_by        VARCHAR(128),
    created_at         DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    INDEX ix_exec_tenant (tenant_id),
    INDEX ix_exec_report (report_id),
    INDEX ix_exec_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
