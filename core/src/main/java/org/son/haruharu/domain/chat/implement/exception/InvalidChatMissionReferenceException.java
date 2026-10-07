package org.son.haruharu.domain.chat.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidChatMissionReferenceException extends BadRequestException {
    public InvalidChatMissionReferenceException() {
        super("INVALID_CHAT_MISSION_REFERENCE", "미션 안내는 시스템 발신자와 올바른 미션·안내 참조가 필요합니다.");
    }
}
