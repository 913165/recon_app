package com.recon.common.dto;

import com.recon.common.enums.MatchStatus;
import com.recon.common.enums.Severity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReconResultDto(
        @NonNull String reconId,
        @NonNull LocalDate reportDate,
        @NonNull String entityId,
        @NonNull String rconCode,
        @Nullable BigDecimal balanceA,
        @Nullable BigDecimal balanceB,
        @Nullable BigDecimal difference,
        @NonNull MatchStatus matchStatus,
        @Nullable Severity severity,
        @NonNull Boolean resolved
) {
}

