package org.son.haruharu.domain.chat.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidChatSyncCursorException extends BadRequestException {
    public InvalidChatSyncCursorException() {
        super("CHAT_SYNC_CURSOR_INVALID", "채팅 동기화 범위가 올바르지 않습니다.");
    }
}
