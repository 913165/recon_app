package com.recon.common.dto;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.List;

public record ParseResult(
        @NonNull List<ReconStagingDto> parsedRecords,
        @NonNull List<ParseErrorRecord> failedRecords,
        long totalRecords,
        long successfulRecords,
        @NonNull BigDecimal computedControlTotal
) {
}

