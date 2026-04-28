package com.recon.common.dto;

import com.recon.common.enums.FileStatus;
import org.jspecify.annotations.NonNull;

public record FileStatusDto(
        @NonNull String fileId,
        @NonNull FileStatus status
) {
}

