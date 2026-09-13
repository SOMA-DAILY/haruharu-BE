package org.son.haruharu.domain.question.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class QuestionNotFoundException extends NotFoundException {
    public QuestionNotFoundException() {
        super("QUESTION_NOT_FOUND", "질문을 찾을 수 없습니다.");
    }
}
