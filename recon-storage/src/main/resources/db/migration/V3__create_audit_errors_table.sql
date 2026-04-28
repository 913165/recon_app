CREATE TABLE recon_audit_errors (
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

CREATE INDEX idx_audit_reprocess ON recon_audit_errors(reprocess_flag) WHERE reprocess_flag = TRUE;
CREATE INDEX idx_audit_file_id ON recon_audit_errors(file_id);

