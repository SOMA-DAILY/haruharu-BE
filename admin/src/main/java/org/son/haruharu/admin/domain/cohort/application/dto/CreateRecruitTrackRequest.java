package org.son.haruharu.admin.domain.cohort.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.cohort.business.command.CreateRecruitTrackCommand;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;

import java.util.List;

/** 트랙 생성. 칸 검증은 도메인이 VALIDATION_ERROR 로 칸별 메시지를 준다. */
public record CreateRecruitTrackRequest(
        @Schema(description = "트랙 키(소문자·숫자·하이픈, 만든 뒤 변경 불가)", example = "seoul-student") String key,
        @Schema(description = "트랙 이름", example = "서울 대학생") String label,
        @Schema(description = "지역", example = "서울") String region,
        @Schema(description = "모집 대상 STUDENT·WORKER", example = "STUDENT") RecruitAudience audience,
        @Schema(description = "최소 나이(19 이상)", example = "20") Integer minAge,
        @Schema(description = "최대 나이", example = "29") Integer maxAge,
        @Schema(description = "트랙별 추가 문항(최대 20개, 생략 시 없음)", nullable = true) List<TrackQuestionRequest> questions,
        @Schema(description = "앱 표시 순서(생략 시 0)", example = "1", nullable = true) Integer displayOrder
) {
    public CreateRecruitTrackCommand toCommand() {
        return new CreateRecruitTrackCommand(key, label, region, audience, minAge, maxAge,
                questions == null ? List.of() : TrackQuestionRequest.toDomain(questions),
                displayOrder == null ? 0 : displayOrder);
    }
}
