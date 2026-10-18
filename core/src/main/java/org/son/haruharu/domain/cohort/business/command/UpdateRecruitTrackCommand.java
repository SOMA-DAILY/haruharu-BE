package org.son.haruharu.domain.cohort.business.command;

import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.TrackQuestion;

import java.util.List;

/** 부분 수정 — null 이 아닌 칸만 바꾼다. questions 는 보내면 목록 전체를 바꾼다. key 는 바꿀 수 없다. */
public record UpdateRecruitTrackCommand(
        String label,
        String region,
        RecruitAudience audience,
        Integer minAge,
        Integer maxAge,
        List<TrackQuestion> questions,
        Integer displayOrder,
        Boolean active
) {
}
