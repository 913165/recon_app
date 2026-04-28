-- Combined migration script: V1 through V5
-- Run this once to create all application tables from scratch

-- ── V1: recon_staging ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS recon_staging (
    id                BIGSERIAL PRIMARY KEY,
    file_id           VARCHAR(50)    NOT NULL,
    source_system     VARCHAR(20)    NOT NULL,
    record_id         VARCHAR(30)    NOT NULL,
    report_date       DATE           NOT NULL,
    entity_id         VARCHAR(10)    NOT NULL,
    entity_name       VARCHAR(100),
    account_code      VARCHAR(15)    NOT NULL DEFAULT '',
    account_desc      VARCHAR(100),
    rcon_code         VARCHAR(10)    NOT NULL,
    balance           NUMERIC(20,2)  NOT NULL,
    dr_cr_ind         CHAR(2)        NOT NULL,
    currency          CHAR(3)        NOT NULL DEFAULT 'USD',
    as_of_time        TIME,
    source_ref        VARCHAR(50),
    comments          VARCHAR(255),
    loaded_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    processed         BOOLEAN        NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_staging_report_date ON recon_staging(report_date);
CREATE INDEX IF NOT EXISTS idx_staging_source ON recon_staging(source_system);
CREATE INDEX IF NOT EXISTS idx_staging_processed ON recon_staging(processed) WHERE processed = FALSE;

-- ── V2: recon_results (partitioned by month) ─────────────────────────────────
CREATE TABLE IF NOT EXISTS recon_results (
    id                BIGSERIAL,
    recon_id          VARCHAR(40)    NOT NULL,
    report_date       DATE           NOT NULL,
    entity_id         VARCHAR(10)    NOT NULL,
    rcon_code         VARCHAR(10)    NOT NULL,
    source_system_a   VARCHAR(20)    NOT NULL,
    balance_a         NUMERIC(20,2),
    source_system_b   VARCHAR(20)    NOT NULL,
    balance_b         NUMERIC(20,2),
    difference        NUMERIC(20,2)  GENERATED ALWAYS AS (balance_a - balance_b) STORED,
    tolerance         NUMERIC(10,2)  NOT NULL DEFAULT 0.00,
    match_status      VARCHAR(15)    NOT NULL,
    severity          VARCHAR(10),
    currency          CHAR(3)        NOT NULL DEFAULT 'USD',
    analyst_assigned  VARCHAR(50),
    break_reason      VARCHAR(255),
    resolved          BOOLEAN        NOT NULL DEFAULT FALSE,
    resolved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, report_date)
) PARTITION BY RANGE (report_date);

CREATE TABLE IF NOT EXISTS recon_results_2026_01 PARTITION OF recon_results FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE IF NOT EXISTS recon_results_2026_02 PARTITION OF recon_results FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE IF NOT EXISTS recon_results_2026_03 PARTITION OF recon_results FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE IF NOT EXISTS recon_results_2026_04 PARTITION OF recon_results FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE IF NOT EXISTS recon_results_2026_05 PARTITION OF recon_results FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE IF NOT EXISTS recon_results_2026_06 PARTITION OF recon_results FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE IF NOT EXISTS recon_results_2026_07 PARTITION OF recon_results FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE IF NOT EXISTS recon_results_2026_08 PARTITION OF recon_results FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE IF NOT EXISTS recon_results_2026_09 PARTITION OF recon_results FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE IF NOT EXISTS recon_results_2026_10 PARTITION OF recon_results FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE IF NOT EXISTS recon_results_2026_11 PARTITION OF recon_results FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE IF NOT EXISTS recon_results_2026_12 PARTITION OF recon_results FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

CREATE INDEX IF NOT EXISTS idx_recon_results_date_brin ON recon_results USING BRIN (report_date);
CREATE INDEX IF NOT EXISTS idx_recon_results_status ON recon_results(match_status);
CREATE INDEX IF NOT EXISTS idx_recon_results_entity ON recon_results(entity_id, report_date);

-- ── V3: recon_audit_errors ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS recon_audit_errors (
    id                BIGSERIAL PRIMARY KEY,
    file_id           VARCHAR(50)    NOT NULL,
    source_system     VARCHAR(20)    NOT NULL,
    record_id         VARCHAR(30),
    report_date       DATE,
    error_type        VARCHAR(30)    NOT NULL,
    error_message     TEXT           NOT NULL,
    raw_record        TEXT,
    retry_count       INT            NOT NULL DEFAULT 0,
    reprocess_flag    BOOLEAN        NOT NULL DEFAULT FALSE,
    reprocessed_at    TIMESTAMPTZ,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_reprocess ON recon_audit_errors(reprocess_flag) WHERE reprocess_flag = TRUE;
CREATE INDEX IF NOT EXISTS idx_audit_file_id ON recon_audit_errors(file_id);

-- ── V4: recon_file_registry ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS recon_file_registry (
    id                BIGSERIAL PRIMARY KEY,
    file_id           VARCHAR(50)    NOT NULL UNIQUE,
    file_name         VARCHAR(255)   NOT NULL,
    source_system     VARCHAR(20)    NOT NULL,
    file_path         VARCHAR(500)   NOT NULL,
    report_date       DATE           NOT NULL,
    total_records     BIGINT,
    loaded_records    BIGINT,
    failed_records    BIGINT,
    control_amount    NUMERIC(25,2),
    file_status       VARCHAR(20)    NOT NULL DEFAULT 'RECEIVED',
    received_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMPTZ,
    kafka_offset      BIGINT,
    kafka_partition   INT
);
CREATE INDEX IF NOT EXISTS idx_file_registry_date ON recon_file_registry(report_date);
CREATE INDEX IF NOT EXISTS idx_file_registry_source ON recon_file_registry(source_system);
CREATE INDEX IF NOT EXISTS idx_file_registry_status ON recon_file_registry(file_status);

-- ── V5: rcon_tolerance_config ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS rcon_tolerance_config (
    id         BIGSERIAL PRIMARY KEY,
    rcon_code  VARCHAR(10) NOT NULL UNIQUE,
    tolerance  NUMERIC(10,2) NOT NULL DEFAULT 0.00
);

INSERT INTO rcon_tolerance_config(rcon_code, tolerance)
VALUES ('RCON0010', 0.00), ('RCON0071', 100.00), ('RCON2122', 500.00), ('RCON2200', 500.00)
ON CONFLICT (rcon_code) DO NOTHING;

-- ── Flyway history: mark all migrations as applied ───────────────────────────
CREATE TABLE IF NOT EXISTS flyway_schema_history (
    installed_rank  INT             NOT NULL,
    version         VARCHAR(50),
    description     VARCHAR(200)    NOT NULL,
    type            VARCHAR(20)     NOT NULL,
    script          VARCHAR(1000)   NOT NULL,
    checksum        INT,
    installed_by    VARCHAR(100)    NOT NULL,
    installed_on    TIMESTAMP       NOT NULL DEFAULT NOW(),
    execution_time  INT             NOT NULL,
    success         BOOLEAN         NOT NULL,
    CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank)
);
CREATE INDEX IF NOT EXISTS flyway_schema_history_s_idx ON flyway_schema_history (success);

INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
VALUES
  (1, '1', 'create staging table',       'SQL', 'V1__create_staging_table.sql',       0, 'recon_user', 10, TRUE),
  (2, '2', 'create recon results table', 'SQL', 'V2__create_recon_results_table.sql',  0, 'recon_user', 10, TRUE),
  (3, '3', 'create audit errors table',  'SQL', 'V3__create_audit_errors_table.sql',   0, 'recon_user', 10, TRUE),
  (4, '4', 'create file registry table', 'SQL', 'V4__create_file_registry_table.sql',  0, 'recon_user', 10, TRUE),
  (5, '5', 'create tolerance table',     'SQL', 'V5__create_tolerance_table.sql',      0, 'recon_user', 10, TRUE)
ON CONFLICT (installed_rank) DO NOTHING;

SELECT 'All tables created successfully' AS result;

