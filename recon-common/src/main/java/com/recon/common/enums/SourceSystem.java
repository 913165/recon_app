package com.recon.common.enums;

/**
 * Indian payment channel source systems.
 *
 * UPI   — Unified Payments Interface  (NPCI real-time 24x7)
 * IMPS  — Immediate Payment Service   (NPCI real-time 24x7, bank-to-bank)
 * NEFT  — National Electronic Funds Transfer (RBI, half-hourly batches)
 * RTGS  — Real-Time Gross Settlement  (RBI, high-value same-day)
 */
public enum SourceSystem {
    UPI,
    IMPS,
    NEFT,
    RTGS
}
