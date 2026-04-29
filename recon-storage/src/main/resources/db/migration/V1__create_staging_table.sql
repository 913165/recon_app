CREATE TABLE recon_staging (
    id                BIGSERIAL PRIMARY KEY,
    file_id           VARCHAR(50)    NOT NULL,
    source_system     VARCHAR(20)    NOT NULL,
    record_id         VARCHAR(30)    NOT NULL,
    report_date       DATE           NOT NULL,
    entity_id         VARCHAR(10)    NOT NULL,
    entity_name       VARCHAR(100),
    account_code      VARCHAR(15)    NOT NULL,
    account_desc      VARCHAR(100),
    rcon_code         VARCHAR(30)    NOT NULL,
    balance           NUMERIC(20,2)  NOT NULL,
    dr_cr_ind         CHAR(2)        NOT NULL,
    currency          CHAR(3)        NOT NULL DEFAULT 'USD',
    as_of_time        TIME,
    source_ref        VARCHAR(50),
    comments          VARCHAR(255),
    loaded_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    processed         BOOLEAN        NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_staging_report_date ON recon_staging(report_date);
CREATE INDEX idx_staging_source ON recon_staging(source_system);
CREATE INDEX idx_staging_processed ON recon_staging(processed) WHERE processed = FALSE;

