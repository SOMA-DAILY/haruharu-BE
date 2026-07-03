package org.son.haruharu.domain.value.business.dto;

import org.son.haruharu.domain.value.implement.ValueQuestion;

public record ValueQuestionResponse(
        Long questionId,
        String questionText,
        String category,
        int displayOrder
) {

    public static ValueQuestionResponse from(ValueQuestion question) {
        return new ValueQuestionResponse(
                question.id(),
                question.questionText(),
                question.category(),
                question.displayOrder()
        );
    }
}
