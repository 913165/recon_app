package com.recon.common.dto;

import org.jspecify.annotations.NonNull;

public record ParseErrorRecord(
        @NonNull String line,
        @NonNull String reason
) {
}

