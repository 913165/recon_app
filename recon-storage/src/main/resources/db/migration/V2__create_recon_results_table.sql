CREATE TABLE recon_results (
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

CREATE TABLE recon_results_2026_01 PARTITION OF recon_results
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE recon_results_2026_02 PARTITION OF recon_results
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE recon_results_2026_03 PARTITION OF recon_results
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE recon_results_2026_04 PARTITION OF recon_results
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE recon_results_2026_05 PARTITION OF recon_results
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE recon_results_2026_06 PARTITION OF recon_results
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE recon_results_2026_07 PARTITION OF recon_results
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE recon_results_2026_08 PARTITION OF recon_results
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE recon_results_2026_09 PARTITION OF recon_results
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE recon_results_2026_10 PARTITION OF recon_results
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE recon_results_2026_11 PARTITION OF recon_results
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE recon_results_2026_12 PARTITION OF recon_results
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

CREATE INDEX idx_recon_results_date_brin ON recon_results USING BRIN (report_date);
CREATE INDEX idx_recon_results_status ON recon_results(match_status);
CREATE INDEX idx_recon_results_entity ON recon_results(entity_id);
CREATE INDEX idx_recon_results_severity ON recon_results(severity) WHERE resolved = FALSE;

