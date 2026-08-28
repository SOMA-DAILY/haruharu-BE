package org.son.haruharu.admin.domain.mission.business.command;

import org.son.haruharu.domain.mission.implement.MissionPeriod;

public record MissionEligibilityCommand(int dayNumber, MissionPeriod period) {
}
