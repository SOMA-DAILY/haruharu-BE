package org.son.haruharu.domain.value.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidValueAnswerException extends BadRequestException {
    public InvalidValueAnswerException() {
        super("INVALID_VALUE_ANSWER", "가치관 답변은 A 또는 B만 허용됩니다.");
    }
}
