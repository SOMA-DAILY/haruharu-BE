package org.son.haruharu.admin.domain.user.implement;

import org.son.haruharu.domain.room.implement.Room;

import java.time.LocalDateTime;

/** 유저의 방 참여 이력 한 건(방 + 입장 시각). */
public record UserParticipation(Room room, LocalDateTime joinedAt) {
}
