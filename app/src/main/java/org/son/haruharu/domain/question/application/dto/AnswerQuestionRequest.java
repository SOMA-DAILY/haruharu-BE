package org.son.haruharu.domain.question.application.dto;

import jakarta.validation.constraints.*;

import org.son.haruharu.domain.question.business.command.AnswerQuestionCommand;

public record AnswerQuestionRequest(@NotBlank String content) {
    public AnswerQuestionCommand toCommand() {
        return new AnswerQuestionCommand(content);
    }
}
