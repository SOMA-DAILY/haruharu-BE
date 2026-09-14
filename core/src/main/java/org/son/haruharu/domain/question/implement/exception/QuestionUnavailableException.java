package org.son.haruharu.domain.question.implement.exception;

import org.son.haruharu.common.exception.ConflictException;

public class QuestionUnavailableException extends ConflictException {
    public QuestionUnavailableException() {
        super("QUESTION_UNAVAILABLE", "현재 질문 또는 답변을 보낼 수 없습니다.");
    }
}
