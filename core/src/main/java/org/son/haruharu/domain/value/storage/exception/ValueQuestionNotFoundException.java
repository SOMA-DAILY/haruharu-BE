package org.son.haruharu.domain.value.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class ValueQuestionNotFoundException extends NotFoundException {

    public ValueQuestionNotFoundException() {
        super("VALUE_QUESTION_NOT_FOUND", "가치관 질문을 찾을 수 없습니다.");
    }
}
