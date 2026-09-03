package org.son.haruharu.domain.room.business.event;

import java.time.LocalDateTime;

public record RoomMemberJoinedEvent(Long roomId, Long userId, LocalDateTime joinedAt) {
}
