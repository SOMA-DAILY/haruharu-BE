package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record RoomMember(
        UUID id,
        UUID roomId,
        UUID userId,
        String gender,
        LocalDate birthday,
        int pointSum,
        LocalDateTime joinedAt
) {
    /** 방 입장 시 열쇠 잔액(pointSum)은 0으로 시작한다(세션 스코프). 성별·생년월일은 본인인증값 스냅샷. */
    public static RoomMember of(UUID roomId, UUID userId, String gender, LocalDate birthday, LocalDateTime joinedAt) {
        return new RoomMember(null, roomId, userId, gender, birthday, 0, joinedAt);
    }

    /** 열쇠 지급/차감. 새 인스턴스를 반환(불변). */
    public RoomMember addPoints(int amount) {
        return new RoomMember(id, roomId, userId, gender, birthday, pointSum + amount, joinedAt);
    }
}
