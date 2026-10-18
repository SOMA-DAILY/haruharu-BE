package org.son.haruharu.admin.domain.cohort.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.cohort.implement.TrackQuestion;
import org.son.haruharu.domain.cohort.implement.TrackQuestionType;

import java.util.List;

/** 트랙별 추가 문항. 형식 검증(키 중복·보기 개수)은 도메인이 칸별로 알려준다. */
public record TrackQuestionRequest(
        @Schema(description = "답을 묶는 키(소문자로 시작, 소문자·숫자·밑줄)", example = "grade") String key,
        @Schema(description = "질문", example = "학년") String label,
        @Schema(description = "TEXT(자유 입력)·CHOICE(보기 중 하나)", example = "CHOICE") TrackQuestionType type,
        @Schema(description = "필수 여부(생략 시 false)", example = "true") Boolean required,
        @Schema(description = "CHOICE 보기 2~20개. TEXT 는 생략", example = "[\"1학년\",\"2학년\",\"3학년\",\"4학년\"]")
        List<String> options
) {
    public TrackQuestion toDomain() {
        return new TrackQuestion(key, label, type, Boolean.TRUE.equals(required), options);
    }

    static List<TrackQuestion> toDomain(List<TrackQuestionRequest> requests) {
        if (requests == null) return null;
        return requests.stream().map(request -> request == null ? null : request.toDomain()).toList();
    }
}
