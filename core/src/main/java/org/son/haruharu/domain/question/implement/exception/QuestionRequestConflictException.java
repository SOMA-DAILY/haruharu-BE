package org.son.haruharu.domain.question.implement.exception;

import org.son.haruharu.common.exception.ConflictException;

public class QuestionRequestConflictException extends ConflictException {
    public QuestionRequestConflictException() {
        super("QUESTION_REQUEST_CONFLICT", "같은 요청으로 다른 질문을 보낼 수 없습니다.");
    }
}
