CREATE TABLE rcon_tolerance_config (
    id         BIGSERIAL PRIMARY KEY,
    rcon_code  VARCHAR(30) NOT NULL UNIQUE,
    tolerance  NUMERIC(10,2) NOT NULL DEFAULT 0.00
);

INSERT INTO rcon_tolerance_config(rcon_code, tolerance)
VALUES ('RCON0010', 0.00), ('RCON0071', 100.00), ('RCON2122', 500.00), ('RCON2200', 500.00);

