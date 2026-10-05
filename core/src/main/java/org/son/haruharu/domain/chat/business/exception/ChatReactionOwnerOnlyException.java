package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

/** 누가 반응했는지는 메시지 주인만(#456). */
public class ChatReactionOwnerOnlyException extends ForbiddenException {
    public ChatReactionOwnerOnlyException() {
        super("CHAT_REACTION_OWNER_ONLY", "메시지를 보낸 사람만 볼 수 있습니다.");
    }
}
