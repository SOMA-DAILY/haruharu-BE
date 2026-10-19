package org.son.haruharu.domain.cohort.business.command;

import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.TrackQuestion;

import java.util.List;

public record CreateRecruitTrackCommand(
        String key,
        String label,
        String region,
        RecruitAudience audience,
        Integer minAge,
        Integer maxAge,
        List<TrackQuestion> questions,
        int displayOrder
) {
}
