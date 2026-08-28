package org.son.haruharu.admin.domain.mission.application.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeMissionStatusRequest(@NotNull Boolean active) {
}
