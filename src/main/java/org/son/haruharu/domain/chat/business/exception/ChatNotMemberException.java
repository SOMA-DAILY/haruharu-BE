package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

public class ChatNotMemberException extends ForbiddenException {
    public ChatNotMemberException() {
        super("CHAT_NOT_MEMBER", "해당 채팅방의 참여자가 아닙니다.");
    }
}
