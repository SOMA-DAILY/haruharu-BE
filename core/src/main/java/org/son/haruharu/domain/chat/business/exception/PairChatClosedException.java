package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class PairChatClosedException extends ConflictException {
    public PairChatClosedException() {
        super("PAIR_CHAT_CLOSED", "1:1 대화 시간이 끝났어요.");
    }
}
