package com.recon.common.dto;

import com.recon.common.enums.FileStatus;
import com.recon.common.enums.SourceSystem;
import org.jspecify.annotations.NonNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record FileRegistryDto(
        @NonNull String fileId,
        @NonNull String fileName,
        @NonNull SourceSystem sourceSystem,
        @NonNull LocalDate reportDate,
        @NonNull FileStatus status,
        long totalRecords,
        long failedRecords,
        @NonNull OffsetDateTime receivedAt
) {
}

