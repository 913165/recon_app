package com.recon.common.dto;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NonNull;

public record ResolveRequest(@NonNull @NotBlank String resolutionNote) {
}

