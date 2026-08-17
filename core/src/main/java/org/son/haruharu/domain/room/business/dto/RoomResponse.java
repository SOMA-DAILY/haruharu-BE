package org.son.haruharu.domain.room.business.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoomResponse(
        @JsonProperty("room_id") Long roomId,
        String status,
        @JsonProperty("scheduled_date") LocalDate scheduledDate,
        @JsonProperty("total_days") int totalDays,
        @JsonProperty("current_day") int currentDay,
        @JsonProperty("started_at") LocalDateTime startedAt,
        @JsonProperty("starts_at") LocalDateTime startsAt,
        @JsonProperty("ends_at") LocalDateTime endsAt,
        @JsonProperty("member_count") int memberCount
) {
    /**
     * @param now 파생 status 계산 기준 시각. 저장 status 는 배치가 맞추는 materialized 값이라
     *            배치가 밀리면 실제와 어긋난다 — 유저에게 내려가는 값은 시각에서 파생한다(#320).
     */
    public static RoomResponse from(Room room, List<RoomMember> members, int currentDay, LocalDateTime now) {
        return new RoomResponse(
            room.id(), room.statusAt(now).name(), room.scheduledDate(),
            room.totalDays(), currentDay, startedAtOf(room), startsAtOf(room), endsAtOf(room), members.size()
        );
    }

    /** 새 모델 방은 startedAt 앵커를 쓰지 않으므로(전이는 시각 기준) starts_at 을 시작 시각으로 노출한다. */
    private static LocalDateTime startedAtOf(Room room) {
        return room.startsAt() != null ? room.startsAt() : room.startedAt();
    }

    /** 방 시작 시각. 구 모델 방(starts_at 미설정)은 scheduledDate 의 하루 시작(09:00)으로 보정한다. */
    private static LocalDateTime startsAtOf(Room room) {
        return room.startsAt() != null
                ? room.startsAt()
                : room.scheduledDate().atTime(Room.DAY_START_HOUR, 0);
    }

    /** 방 종료 시각. 구 모델 방은 마지막 날(scheduledDate + totalDays)의 하루 시작으로 보정한다. */
    private static LocalDateTime endsAtOf(Room room) {
        return room.endsAt() != null
                ? room.endsAt()
                : room.scheduledDate().plusDays(room.totalDays()).atTime(Room.DAY_START_HOUR, 0);
    }
}
