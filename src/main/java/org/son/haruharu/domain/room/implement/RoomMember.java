package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;
import java.util.UUID;

public record RoomMember(
        UUID id,
        UUID roomId,
        UUID userId,
        String gender,
        int pointSum,
        LocalDateTime joinedAt
) {
    /** 방 입장 시 열쇠 잔액(pointSum)은 0으로 시작한다(세션 스코프). */
    public static RoomMember of(UUID roomId, UUID userId, String gender, LocalDateTime joinedAt) {
        return new RoomMember(null, roomId, userId, gender, 0, joinedAt);
    }

    /** 열쇠 지급/차감. 새 인스턴스를 반환(불변). */
    public RoomMember addPoints(int amount) {
        return new RoomMember(id, roomId, userId, gender, pointSum + amount, joinedAt);
    }
}
