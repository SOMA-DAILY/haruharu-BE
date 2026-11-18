package org.son.haruharu.domain.chat.implement;

import java.util.List;

/** 고정 범위의 조회 결과. nextAfterId는 이 페이지를 적용한 뒤 확정할 수 있는 위치다. */
public record ChatMessageSyncPage(List<ChatMessage> messages, long targetId, long nextAfterId, boolean hasMore) {
    public ChatMessageSyncPage {
        messages = List.copyOf(messages);
    }
}
