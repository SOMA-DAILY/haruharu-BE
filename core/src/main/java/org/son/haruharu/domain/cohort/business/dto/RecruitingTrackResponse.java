package org.son.haruharu.domain.cohort.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;

import java.time.LocalDateTime;
import java.util.List;

/** 앱에 보이는 트랙. 열린 기수가 있는 활성 트랙만 내려간다. */
public record RecruitingTrackResponse(
        @Schema(description = "트랙 id", example = "1") Long trackId,
        @Schema(description = "트랙 키", example = "seoul-student") String key,
        @Schema(description = "트랙 이름", example = "서울 대학생") String label,
        @Schema(description = "지역", example = "서울") String region,
        @Schema(description = "모집 대상", example = "STUDENT") RecruitAudience audience,
        @Schema(description = "최소 나이", example = "20") int minAge,
        @Schema(description = "최대 나이", example = "29") int maxAge,
        @Schema(description = "트랙별 추가 문항(신청 화면에서 학교·학과·번호 뒤에 묻는다)") List<TrackQuestionResponse> questions,
        @Schema(description = "열린 기수") RecruitingCohortResponse cohort
) {
    public static RecruitingTrackResponse of(RecruitTrack track, Cohort cohort, LocalDateTime now) {
        return new RecruitingTrackResponse(track.id(), track.key(), track.label(), track.region(), track.audience(),
                track.minAge(), track.maxAge(), TrackQuestionResponse.fromAll(track.questions()),
                RecruitingCohortResponse.of(cohort, track.label(), now));
    }
}
