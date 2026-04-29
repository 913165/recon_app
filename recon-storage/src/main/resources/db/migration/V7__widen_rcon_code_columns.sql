-- V7: Widen rcon_code columns to VARCHAR(30) for Indian payment channel codes
-- Indian payment RCON codes (e.g. RECON_UPI_CR) are longer than original VARCHAR(10).
ALTER TABLE rcon_tolerance_config ALTER COLUMN rcon_code TYPE VARCHAR(30);
ALTER TABLE recon_staging         ALTER COLUMN rcon_code TYPE VARCHAR(30);
ALTER TABLE recon_results         ALTER COLUMN rcon_code TYPE VARCHAR(30);

