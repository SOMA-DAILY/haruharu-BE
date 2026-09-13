package org.son.haruharu.domain.question.implement.exception;

import org.son.haruharu.common.exception.ForbiddenException;

public class QuestionAccessDeniedException extends ForbiddenException {
    public QuestionAccessDeniedException() {
        super("QUESTION_ACCESS_DENIED", "질문에 접근할 수 없습니다.");
    }
}
