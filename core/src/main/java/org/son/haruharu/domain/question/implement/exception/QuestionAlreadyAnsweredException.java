package org.son.haruharu.domain.question.implement.exception;

import org.son.haruharu.common.exception.ConflictException;

public class QuestionAlreadyAnsweredException extends ConflictException {
    public QuestionAlreadyAnsweredException() {
        super("QUESTION_ALREADY_ANSWERED", "이미 답변한 질문입니다.");
    }
}
