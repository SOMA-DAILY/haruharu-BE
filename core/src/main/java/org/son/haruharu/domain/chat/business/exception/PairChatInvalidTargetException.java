package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class PairChatInvalidTargetException extends BadRequestException {
    public PairChatInvalidTargetException() {
        super("PAIR_CHAT_INVALID_TARGET", "1:1 대화를 열 수 없는 상대예요.");
    }
}
