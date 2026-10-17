package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

public class ChatRoomClosedException extends ForbiddenException {
    public ChatRoomClosedException() {
        super("CHAT_ROOM_CLOSED", "종료되었거나 이용할 수 없는 채팅방입니다.");
    }
}
