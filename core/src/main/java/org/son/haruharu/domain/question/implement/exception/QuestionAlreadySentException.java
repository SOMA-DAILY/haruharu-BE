package org.son.haruharu.domain.question.implement.exception;

import org.son.haruharu.common.exception.ConflictException;

public class QuestionAlreadySentException extends ConflictException {
    public QuestionAlreadySentException() {
        super("QUESTION_ALREADY_SENT", "이미 질문을 보낸 상대입니다.");
    }
}
