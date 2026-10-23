package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class PairChatLeaveNotAllowedException extends ConflictException {
    public PairChatLeaveNotAllowedException() {
        super("PAIR_CHAT_LEAVE_NOT_ALLOWED", "1:1 대화방은 나갈 수 없어요.");
    }
}
