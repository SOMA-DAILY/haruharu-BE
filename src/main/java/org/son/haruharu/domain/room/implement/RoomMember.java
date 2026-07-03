package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RoomMember(
        Long id,
        Long roomId,
        Long userId,
        String gender,
        LocalDate birthday,
        LocalDateTime joinedAt
) {
    /** 방 입장. 성별·생년월일은 본인인증값 스냅샷. */
    public static RoomMember of(Long roomId, Long userId, String gender, LocalDate birthday, LocalDateTime joinedAt) {
        return new RoomMember(null, roomId, userId, gender, birthday, joinedAt);
    }
}
