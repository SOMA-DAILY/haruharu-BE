package org.son.haruharu.domain.chat.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class ChatRoomNotFoundException extends NotFoundException {
    public ChatRoomNotFoundException() {
        super("CHAT_ROOM_NOT_FOUND", "존재하지 않는 채팅방입니다.");
    }
}
