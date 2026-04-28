package com.recon.common.dto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;

public record ReconAuditDto(
        @NonNull String reconId,
        @NonNull String action,
        @Nullable String note,
        @NonNull OffsetDateTime timestamp
) {
}

