package org.son.haruharu.domain.mission.implement;

import java.util.List;

public record MissionCatalogEntry(
        MissionRevision revision,
        List<MissionRevisionEligibility> eligibilities
) {
    public MissionCatalogEntry {
        eligibilities = List.copyOf(eligibilities);
    }

    public boolean eligibleFor(int dayNumber, MissionPeriod period) {
        return eligibilities.stream().anyMatch(eligibility -> eligibility.dayNumber() == dayNumber
                && eligibility.period() == period);
    }
}
