package org.son.haruharu.admin.domain.mission.business.command;

public record CreateMissionCommand(
        String description,
        int points
) {
}
