package com.recon.common.dto;

import com.recon.common.enums.SourceSystem;
import org.jspecify.annotations.NonNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record FileArrivedEvent(
        @NonNull String fileId,
        @NonNull String fileName,
        @NonNull SourceSystem sourceSystem,
        @NonNull String filePath,
        @NonNull LocalDate reportDate,
        @NonNull OffsetDateTime arrivedAt
) {
}

