package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

/** 내 메시지 반응 금지(#456). */
public class CannotReactToOwnMessageException extends ForbiddenException {
    public CannotReactToOwnMessageException() {
        super("CHAT_REACTION_OWN_MESSAGE", "내 메시지에는 반응할 수 없습니다.");
    }
}
