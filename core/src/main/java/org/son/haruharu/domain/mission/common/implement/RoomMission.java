package org.son.haruharu.domain.mission.common.implement;

import java.time.LocalDateTime;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;

/** 미션의 시간과 취소 여부가 원본이다. 안내의 발송 성공 여부는 생명주기에 관여하지 않는다. */
public record RoomMission(
        Long id, Long roomId, MissionType type, MissionDefinition details,
        LocalDateTime opensAt, LocalDateTime closesAt, LocalDateTime canceledAt,
        String createdBy, String updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public RoomMission {
        if (roomId == null || roomId <= 0) throw new InvalidMissionException("room_id", "방을 선택해 주세요");
        if (type == null) throw new InvalidMissionException("type", "미션 유형을 선택해 주세요");
        if (opensAt == null) throw new InvalidMissionException("opens_at", "시작 시각을 지정해 주세요");
        if (closesAt != null && !closesAt.isAfter(opensAt)) {
            throw new InvalidMissionException("closes_at", "마감은 시작 시각 이후여야 해요");
        }
        if (details == null || details.type() != type) {
            throw new InvalidMissionException("details", "미션 유형에 맞는 내용을 입력해 주세요");
        }
    }

    public static RoomMission create(Long roomId, MissionType type, MissionDefinition details, LocalDateTime opensAt,
                                     LocalDateTime closesAt, String actor) {
        return new RoomMission(null, roomId, type, details, opensAt, closesAt,
                null, actor, actor, null, null);
    }

    public boolean visibleAt(LocalDateTime now, Room room) {
        return !now.isBefore(opensAt) && room.statusAt(now) != RoomStatus.PENDING
                && (room.startsAt() == null || !now.isBefore(room.startsAt()));
    }

    public MissionStatus statusAt(LocalDateTime now, Room room) {
        if (canceledAt != null || room.statusAt(now) == RoomStatus.CANCELLED) return MissionStatus.CANCELED;
        if (room.statusAt(now) == RoomStatus.ENDED) return MissionStatus.CLOSED;
        LocalDateTime deadline = effectiveClosesAt(room);
        if (deadline != null && !now.isBefore(deadline)) return MissionStatus.CLOSED;
        if (now.isBefore(opensAt) || room.statusAt(now) == RoomStatus.PENDING) return MissionStatus.SCHEDULED;
        return MissionStatus.OPEN;
    }

    /** null 마감은 변경된 방 종료 시각도 따른다. 명시적 마감 역시 방 종료를 넘겨 참여를 허용하지 않는다. */
    public LocalDateTime effectiveClosesAt(Room room) {
        if (closesAt == null) return room.endsAt();
        return room.endsAt() == null || closesAt.isBefore(room.endsAt()) ? closesAt : room.endsAt();
    }

    public RoomMission update(MissionDefinition details, LocalDateTime opensAt,
                              LocalDateTime closesAt, String actor, LocalDateTime now) {
        return new RoomMission(id, roomId, type, details, opensAt, closesAt, canceledAt,
                createdBy, actor, createdAt, now);
    }

    public RoomMission cancel(String actor, LocalDateTime now) {
        return new RoomMission(id, roomId, type, details, opensAt, closesAt,
                canceledAt == null ? now : canceledAt, createdBy, actor, createdAt, now);
    }
}
