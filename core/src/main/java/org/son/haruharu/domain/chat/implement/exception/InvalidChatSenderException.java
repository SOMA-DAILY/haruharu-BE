package org.son.haruharu.domain.chat.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidChatSenderException extends BadRequestException {
    public InvalidChatSenderException() {
        super("INVALID_CHAT_SENDER", "일반 메시지는 유저 발신자가 필요하고 시스템 메시지는 유저 발신자를 가질 수 없습니다.");
    }
}
