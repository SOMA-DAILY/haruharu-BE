package org.son.haruharu.domain.home.business.dto;

import java.time.LocalDateTime;

import org.son.haruharu.domain.matching.business.dto.QueueStatusResponse;
import org.son.haruharu.domain.room.business.dto.RoomResponse;

/**
 * 메인 홈 집계 응답. confirmAt은 신청 마감이자 결과 발표 시각(다음 매칭 확정 시각)으로,
 * 신청 전 "마감까지"·신청 후 "결과 안내까지" 카운트다운의 공통 기준이다.
 */
public record HomeResponse(
        long pointBalance,
        MatchingStatus matching,
        ActiveRoom activeRoom
) {

    public record MatchingStatus(String status, LocalDateTime queuedAt, LocalDateTime confirmAt) {}

    public record ActiveRoom(
            Long roomId,
            String status,
            int currentDay,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {}

    public static HomeResponse of(long pointBalance, QueueStatusResponse queue, RoomResponse room,
                                  LocalDateTime confirmAt, int dayStartHour) {
        MatchingStatus matching = new MatchingStatus(queue.status(), queue.queuedAt(), confirmAt);
        ActiveRoom activeRoom = room == null ? null : new ActiveRoom(
                room.roomId(),
                room.status(),
                room.currentDay(),
                room.scheduledDate().atTime(dayStartHour, 0),
                room.scheduledDate().plusDays(room.totalDays()).atTime(dayStartHour, 0)
        );
        return new HomeResponse(pointBalance, matching, activeRoom);
    }
}
