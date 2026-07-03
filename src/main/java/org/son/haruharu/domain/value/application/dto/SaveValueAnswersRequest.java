package org.son.haruharu.domain.value.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.son.haruharu.domain.value.business.command.SaveValueAnswersCommand;

import java.util.List;

public record SaveValueAnswersRequest(
        @NotEmpty @Valid List<AnswerRequest> answers
) {

    public record AnswerRequest(
            @NotNull Long questionId,
            @NotBlank String answer
    ) {}

    public SaveValueAnswersCommand toCommand() {
        return new SaveValueAnswersCommand(
                answers.stream()
                        .map(a -> new SaveValueAnswersCommand.AnswerItem(a.questionId(), a.answer()))
                        .toList()
        );
    }
}
