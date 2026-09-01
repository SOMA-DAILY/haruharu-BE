package org.son.haruharu.domain.mission.implement;

import java.math.BigDecimal;
import java.util.List;

public record MissionCandidateSnapshot(List<Candidate> candidates) {

    public MissionCandidateSnapshot {
        candidates = List.copyOf(candidates);
    }

    public record Candidate(
            Long missionRevisionId,
            Long missionId,
            int revisionNumber,
            boolean fallback,
            BigDecimal propensity
    ) {
    }
}
