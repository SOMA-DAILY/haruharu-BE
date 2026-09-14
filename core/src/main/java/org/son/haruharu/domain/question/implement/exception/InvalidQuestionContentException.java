package org.son.haruharu.domain.question.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidQuestionContentException extends BadRequestException {
    public InvalidQuestionContentException() {
        super("INVALID_QUESTION_CONTENT", "질문은 1~50자, 답변은 1~100자로 입력해주세요.");
    }
}
