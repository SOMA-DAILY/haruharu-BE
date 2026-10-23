package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class PairChatNotOpenException extends ConflictException {
    public PairChatNotOpenException() {
        super("PAIR_CHAT_NOT_OPEN", "지금은 1:1 대화 시간이 아니에요.");
    }
}
