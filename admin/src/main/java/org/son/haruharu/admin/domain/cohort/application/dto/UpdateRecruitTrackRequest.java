package org.son.haruharu.admin.domain.cohort.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.cohort.business.command.UpdateRecruitTrackCommand;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;

import java.util.List;

/** 부분 수정 — 보낸 칸만 바꾼다. questions 는 보내면 목록 전체를 바꾼다. key 는 바꿀 수 없다. */
public record UpdateRecruitTrackRequest(
        @Schema(description = "트랙 이름", example = "서울 대학생") String label,
        @Schema(description = "지역", example = "서울") String region,
        @Schema(description = "모집 대상", example = "STUDENT") RecruitAudience audience,
        @Schema(description = "최소 나이", example = "20") Integer minAge,
        @Schema(description = "최대 나이", example = "29") Integer maxAge,
        @Schema(description = "추가 문항 전체") List<TrackQuestionRequest> questions,
        @Schema(description = "앱 표시 순서", example = "1") Integer displayOrder,
        @Schema(description = "활성 여부. 공개 중인 기수가 있으면 끌 수 없다", example = "true") Boolean active
) {
    public UpdateRecruitTrackCommand toCommand() {
        return new UpdateRecruitTrackCommand(label, region, audience, minAge, maxAge,
                TrackQuestionRequest.toDomain(questions), displayOrder, active);
    }
}
