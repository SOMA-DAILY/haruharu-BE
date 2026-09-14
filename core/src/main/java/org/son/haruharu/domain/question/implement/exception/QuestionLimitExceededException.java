package org.son.haruharu.domain.question.implement.exception;

import org.son.haruharu.common.exception.ConflictException;

public class QuestionLimitExceededException extends ConflictException {
    public QuestionLimitExceededException() {
        super("QUESTION_LIMIT_EXCEEDED", "이 방의 질문권을 모두 사용했습니다.");
    }
}
