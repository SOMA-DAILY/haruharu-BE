package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

public class ChatPartnerBlockedException extends ForbiddenException {

    public ChatPartnerBlockedException() {
        super("CHAT_PARTNER_BLOCKED", "차단 관계인 상대와의 채팅방입니다.");
    }
}
