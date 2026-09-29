package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.NotFoundException;

/** 채팅방에 없는 메시지(#456). */
public class ChatMessageNotFoundException extends NotFoundException {
    public ChatMessageNotFoundException() {
        super("CHAT_MESSAGE_NOT_FOUND", "존재하지 않는 메시지입니다.");
    }
}
