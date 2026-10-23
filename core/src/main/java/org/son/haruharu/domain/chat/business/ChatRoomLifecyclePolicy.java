package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.chat.business.exception.ChatRoomClosedException;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.springframework.stereotype.Component;

/** GROUP·PAIR는 저장된 close_at(방 종료 시 기록)으로 차단한다. 원본 방 조회 없이 스케줄러의 종료 처리 지연을 허용한다. */
@Component
public class ChatRoomLifecyclePolicy {
    public boolean isActive(ChatRoom chat) {
        return chat.type() == ChatRoomType.DIRECT || chat.closeAt() == null;
    }

    public void requireActive(ChatRoom chat) {
        if (!isActive(chat)) throw new ChatRoomClosedException();
    }
}
