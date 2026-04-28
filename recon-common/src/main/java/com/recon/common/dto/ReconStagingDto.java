package com.recon.common.dto;

import com.recon.common.annotation.ValidRconCode;
import com.recon.common.enums.DrCrIndicator;
import com.recon.common.enums.SourceSystem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReconStagingDto(
        @NonNull @NotBlank String fileId,
        @NonNull @NotNull SourceSystem sourceSystem,
        @NonNull @NotBlank String recordId,
        @NonNull @NotNull LocalDate reportDate,
        @NonNull @NotBlank String entityId,
        @NonNull @NotBlank @ValidRconCode String rconCode,
        @NonNull @NotNull @PositiveOrZero BigDecimal balance,
        @NonNull @NotNull DrCrIndicator drCrInd,
        @NonNull @NotBlank String currency,
        @Nullable String sourceRef,
        @Nullable String comments
) {
}

