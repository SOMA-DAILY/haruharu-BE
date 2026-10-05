package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

/** 운영자가 지운 메시지에는 반응할 수 없다(#457). */
public class CannotReactToDeletedMessageException extends BadRequestException {
    public CannotReactToDeletedMessageException() {
        super("CHAT_REACTION_DELETED_MESSAGE", "삭제된 메시지에는 반응할 수 없습니다.");
    }
}
