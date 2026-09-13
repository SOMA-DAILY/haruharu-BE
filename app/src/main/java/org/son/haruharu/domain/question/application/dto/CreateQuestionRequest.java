package org.son.haruharu.domain.question.application.dto;

import jakarta.validation.constraints.*;

import org.son.haruharu.domain.question.business.command.CreateQuestionCommand;

import java.util.UUID;

public record CreateQuestionRequest(
        @NotNull Long recipientMemberId, @NotBlank String content, @NotNull UUID requestId) {
    public CreateQuestionCommand toCommand() {
        return new CreateQuestionCommand(recipientMemberId, content, requestId);
    }
}
