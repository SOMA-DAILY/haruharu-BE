package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

public class ChatPartnerWithdrawnException extends ForbiddenException {
    public ChatPartnerWithdrawnException() {
        super("CHAT_PARTNER_WITHDRAWN", "상대방이 탈퇴한 채팅방입니다.");
    }
}
