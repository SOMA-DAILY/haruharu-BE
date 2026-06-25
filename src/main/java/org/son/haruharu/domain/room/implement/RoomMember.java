package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;
import java.util.UUID;

public record RoomMember(
        UUID id,
        UUID roomId,
        UUID userId,
        String gender,
        LocalDateTime joinedAt
) {
    public static RoomMember of(UUID roomId, UUID userId, String gender, LocalDateTime joinedAt) {
        return new RoomMember(null, roomId, userId, gender, joinedAt);
    }
}
