package org.son.haruharu.domain.value.business.dto;

import org.son.haruharu.domain.value.implement.ValueQuestion;

import java.util.List;

public record ValueQuestionsResponse(List<ValueQuestionResponse> questions) {

    public static ValueQuestionsResponse from(List<ValueQuestion> questions) {
        return new ValueQuestionsResponse(
                questions.stream()
                        .map(ValueQuestionResponse::from)
                        .toList()
        );
    }
}
