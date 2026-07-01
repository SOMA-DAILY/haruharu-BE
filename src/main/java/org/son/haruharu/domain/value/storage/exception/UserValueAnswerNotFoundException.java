package org.son.haruharu.domain.value.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class UserValueAnswerNotFoundException extends NotFoundException {

    public UserValueAnswerNotFoundException() {
        super("VALUE_ANSWER_NOT_FOUND", "가치관 답변을 찾을 수 없습니다.");
    }
}
