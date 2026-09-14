package org.son.haruharu.domain.chat.business.event;

import java.util.List;

public record ChatRoomClosedEvent(Long chatRoomId, List<Long> memberUserIds) {
    public ChatRoomClosedEvent {
        memberUserIds = List.copyOf(memberUserIds);
    }
}
