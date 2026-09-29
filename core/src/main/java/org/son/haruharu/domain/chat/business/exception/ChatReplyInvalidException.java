package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

/** 답장 원문이 같은 채팅방 메시지가 아니거나 없음(#456). */
public class ChatReplyInvalidException extends BadRequestException {
    public ChatReplyInvalidException() {
        super("CHAT_REPLY_INVALID", "답장할 수 없는 메시지입니다.");
    }
}
