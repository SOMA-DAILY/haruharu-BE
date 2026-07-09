package org.son.haruharu.domain.profile.application.dto;

import jakarta.validation.constraints.NotBlank;

public record PresignedUrlRequest(@NotBlank String filename) {}
