-- V6: Indian Payment Channel — RCON tolerance configuration
-- Replaces the original V5 generic bank codes with NPCI/RBI reconciliation codes.
--
-- Indian Payment Reconciliation Codes (NPCI / RBI defined):
--
--   UPI Codes  (NPCI UPI Switch ↔ Bank CBS)
--   ──────────────────────────────────────────────────────────────────
--   RECON_UPI_CR   Total UPI credit transactions settled         (₹ tolerance: ₹1.00  — paise rounding)
--   RECON_UPI_DR   Total UPI debit transactions settled          (₹ tolerance: ₹1.00)
--   RECON_UPI_REV  UPI reversals / refunds                       (₹ tolerance: ₹0.00  — exact match)
--   RECON_UPI_CHB  UPI chargebacks raised                        (₹ tolerance: ₹0.00)
--
--   IMPS Codes (NPCI IMPS Switch ↔ Bank CBS)
--   ──────────────────────────────────────────────────────────────────
--   RECON_IMPS_CR  Total IMPS inward credits                     (₹ tolerance: ₹0.00)
--   RECON_IMPS_DR  Total IMPS outward debits                     (₹ tolerance: ₹0.00)
--   RECON_IMPS_RET IMPS returns (beneficiary account invalid)    (₹ tolerance: ₹0.00)
--
--   NEFT Codes (RBI NEFT Settlement ↔ Bank CBS)
--   ──────────────────────────────────────────────────────────────────
--   RECON_NEFT_CR  NEFT inward credit batch totals               (₹ tolerance: ₹0.00)
--   RECON_NEFT_DR  NEFT outward debit batch totals               (₹ tolerance: ₹0.00)
--   RECON_NEFT_RET NEFT returns (invalid account / IFSC)         (₹ tolerance: ₹0.00)
--
--   RTGS Codes (RBI RTGS Gross Settlement ↔ Bank CBS)
--   ──────────────────────────────────────────────────────────────────
--   RECON_RTGS_CR  RTGS inward gross credits (high value ≥ ₹2L)  (₹ tolerance: ₹0.00  — exact gross settlement)
--   RECON_RTGS_DR  RTGS outward gross debits                     (₹ tolerance: ₹0.00)
--   RECON_RTGS_REJ RTGS rejected transactions                    (₹ tolerance: ₹0.00)

-- Clear old RCON codes and insert Indian payment codes
TRUNCATE TABLE rcon_tolerance_config;

INSERT INTO rcon_tolerance_config (rcon_code, tolerance) VALUES
-- UPI — paise rounding allowed for credit/debit; zero for reversals and chargebacks
('RECON_UPI_CR',   1.00),
('RECON_UPI_DR',   1.00),
('RECON_UPI_REV',  0.00),
('RECON_UPI_CHB',  0.00),

-- IMPS — exact match required (bank-to-bank inter-bank settlement)
('RECON_IMPS_CR',  0.00),
('RECON_IMPS_DR',  0.00),
('RECON_IMPS_RET', 0.00),

-- NEFT — exact match required (RBI batch settlement)
('RECON_NEFT_CR',  0.00),
('RECON_NEFT_DR',  0.00),
('RECON_NEFT_RET', 0.00),

-- RTGS — exact gross settlement, zero tolerance
('RECON_RTGS_CR',  0.00),
('RECON_RTGS_DR',  0.00),
('RECON_RTGS_REJ', 0.00)

ON CONFLICT (rcon_code) DO UPDATE SET tolerance = EXCLUDED.tolerance;

