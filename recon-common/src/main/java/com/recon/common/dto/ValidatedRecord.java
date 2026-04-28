package com.recon.common.dto;

import com.recon.common.enums.SourceSystem;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ValidatedRecord(
        @NonNull String fileId,
        @NonNull SourceSystem sourceSystem,
        @NonNull String recordId,
        @NonNull LocalDate reportDate,
        @NonNull String entityId,
        @NonNull String rconCode,
        @NonNull BigDecimal balance,
        @NonNull String currency
) {
}

