package org.son.haruharu.domain.mission.implement;

import org.son.haruharu.domain.mission.implement.exception.InvalidMissionRevisionEligibilityException;

public record MissionRevisionEligibility(
        Long id,
        Long missionRevisionId,
        int dayNumber,
        MissionPeriod period
) {
    public static MissionRevisionEligibility of(Long missionRevisionId, int dayNumber, MissionPeriod period) {
        if (missionRevisionId == null || dayNumber < 1 || dayNumber > 3 || period == null) {
            throw new InvalidMissionRevisionEligibilityException();
        }
        return new MissionRevisionEligibility(null, missionRevisionId, dayNumber, period);
    }
}
