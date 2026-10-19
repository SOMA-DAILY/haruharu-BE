package org.son.haruharu.domain.cohort.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;

import java.time.LocalDateTime;
import java.util.List;

/** 어드민 트랙 응답. */
public record RecruitTrackResponse(
        @Schema(description = "트랙 id", example = "1") Long id,
        @Schema(description = "트랙 키(고정)", example = "seoul-student") String key,
        @Schema(description = "트랙 이름", example = "서울 대학생") String label,
        @Schema(description = "지역", example = "서울") String region,
        @Schema(description = "모집 대상", example = "STUDENT") RecruitAudience audience,
        @Schema(description = "최소 나이", example = "20") int minAge,
        @Schema(description = "최대 나이", example = "29") int maxAge,
        @Schema(description = "트랙별 추가 문항") List<TrackQuestionResponse> questions,
        @Schema(description = "앱 표시 순서(작을수록 먼저)", example = "1") int displayOrder,
        @Schema(description = "활성 여부. 비활성 트랙은 앱에 보이지 않는다", example = "true") boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RecruitTrackResponse from(RecruitTrack track) {
        return new RecruitTrackResponse(track.id(), track.key(), track.label(), track.region(), track.audience(),
                track.minAge(), track.maxAge(), TrackQuestionResponse.fromAll(track.questions()),
                track.displayOrder(), track.active(), track.createdAt(), track.updatedAt());
    }
}
