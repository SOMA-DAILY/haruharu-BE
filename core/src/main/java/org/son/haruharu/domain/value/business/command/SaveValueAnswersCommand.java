package org.son.haruharu.domain.value.business.command;

import java.util.List;

public record SaveValueAnswersCommand(List<AnswerItem> answers) {

    public record AnswerItem(Long questionId, String answer) {}
}
