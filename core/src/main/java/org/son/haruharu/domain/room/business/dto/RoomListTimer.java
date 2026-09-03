package org.son.haruharu.domain.room.business.dto;

import java.time.LocalDateTime;

import org.son.haruharu.domain.room.implement.Room;

/** 홈 방 카드의 다음 갱신 경계. 오늘 화면 타이머와 별도의 계약이다. */
public record RoomListTimer(TimerType timerType, LocalDateTime timerTargetAt, LocalDateTime refreshAt) {

    public enum TimerType {
        ROOM_STARTS_AT,
        FINAL_SELECTION_OPENS_AT,
        FINAL_SELECTION_CLOSES_AT,
        RESULT_AT
    }

    public static RoomListTimer from(Room room, LocalDateTime now) {
        LocalDateTime startsAt = room.startsAt();
        LocalDateTime opensAt = room.finalSelectionOpensAt();
        LocalDateTime closesAt = room.finalSelectionClosesAt();
        LocalDateTime resultAt = room.resultAt();
        LocalDateTime endsAt = room.endsAt();
        if (startsAt == null || opensAt == null || closesAt == null || resultAt == null || endsAt == null) {
            return new RoomListTimer(null, null, null);
        }
        if (now.isBefore(startsAt)) {
            return new RoomListTimer(TimerType.ROOM_STARTS_AT, startsAt, startsAt);
        }
        if (now.isBefore(opensAt)) {
            return new RoomListTimer(TimerType.FINAL_SELECTION_OPENS_AT, opensAt, opensAt);
        }
        if (now.isBefore(closesAt)) {
            return new RoomListTimer(TimerType.FINAL_SELECTION_CLOSES_AT, closesAt, closesAt);
        }
        if (now.isBefore(resultAt)) {
            return new RoomListTimer(TimerType.RESULT_AT, resultAt, resultAt);
        }
        if (now.isBefore(endsAt)) {
            return new RoomListTimer(null, null, endsAt);
        }
        return new RoomListTimer(null, null, null);
    }
}
