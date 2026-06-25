package org.son.haruharu.domain.value.business.command;

import java.util.List;
import java.util.UUID;

public record SaveValueAnswersCommand(List<AnswerItem> answers) {

    public record AnswerItem(UUID questionId, String answer) {}
}
