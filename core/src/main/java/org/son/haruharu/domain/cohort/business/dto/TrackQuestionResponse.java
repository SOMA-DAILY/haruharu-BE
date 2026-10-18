package org.son.haruharu.domain.cohort.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.cohort.implement.TrackQuestion;
import org.son.haruharu.domain.cohort.implement.TrackQuestionType;

import java.util.List;

public record TrackQuestionResponse(
        @Schema(description = "답을 묶는 키(트랙 안에서 유일)", example = "grade") String key,
        @Schema(description = "질문", example = "학년") String label,
        @Schema(description = "TEXT(자유 입력)·CHOICE(보기 중 하나)", example = "CHOICE") TrackQuestionType type,
        @Schema(description = "필수 여부", example = "true") boolean required,
        @Schema(description = "CHOICE 보기. TEXT 는 빈 배열") List<String> options
) {
    public static TrackQuestionResponse from(TrackQuestion question) {
        return new TrackQuestionResponse(question.key(), question.label(), question.type(), question.required(),
                question.options());
    }

    public static List<TrackQuestionResponse> fromAll(List<TrackQuestion> questions) {
        return questions.stream().map(TrackQuestionResponse::from).toList();
    }
}
