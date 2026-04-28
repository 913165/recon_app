CREATE TABLE recon_file_registry (
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

