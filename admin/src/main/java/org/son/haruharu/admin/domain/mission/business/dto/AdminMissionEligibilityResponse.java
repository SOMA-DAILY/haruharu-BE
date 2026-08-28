package org.son.haruharu.admin.domain.mission.business.dto;

import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.MissionRevisionEligibility;

public record AdminMissionEligibilityResponse(int dayNumber, MissionPeriod period) {

    public static AdminMissionEligibilityResponse from(MissionRevisionEligibility eligibility) {
        return new AdminMissionEligibilityResponse(eligibility.dayNumber(), eligibility.period());
    }
}
