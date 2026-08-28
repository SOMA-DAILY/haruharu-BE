package org.son.haruharu.admin.domain.mission.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.son.haruharu.admin.domain.mission.business.command.MissionEligibilityCommand;
import org.son.haruharu.domain.mission.implement.MissionPeriod;

public record MissionEligibilityRequest(
        @Min(1) @Max(3) int dayNumber,
        @NotNull MissionPeriod period
) {
    public MissionEligibilityCommand toCommand() {
        return new MissionEligibilityCommand(dayNumber, period);
    }
}
