package org.son.haruharu.domain.room.implement;

import java.util.UUID;

public record RoomMember(
        UUID id,
        UUID roomId,
        UUID userId,
        String gender
) {
    public static RoomMember of(UUID roomId, UUID userId, String gender) {
        return new RoomMember(null, roomId, userId, gender);
    }
}
