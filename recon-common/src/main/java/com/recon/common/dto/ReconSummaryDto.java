package com.recon.common.dto;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReconSummaryDto(
        @NonNull LocalDate reportDate,
        long totalRecords,
        long matchedCount,
        long breakCount,
        long unmatchedCount,
        @NonNull BigDecimal matchRate,
        @NonNull BigDecimal totalBreakAmount
) {
}

