package org.son.haruharu.domain.home.business.dto;

import java.time.LocalDateTime;

import org.son.haruharu.domain.matching.business.dto.QueueStatusResponse;
import org.son.haruharu.domain.room.business.dto.PendingMatchResultResponse;
import org.son.haruharu.domain.room.business.dto.RoomResponse;

/**
 * 메인 홈 집계 응답.
 * now = 응답 생성 시점 기준 시각(주입 Clock, dev 가상 시계 반영) → 클라 카운트다운의 기기 시계 오차 보정 기준.
 * applyDeadlineAt = 신청 마감(21:00) → 신청 전 "마감까지" 카운트다운 기준.
 * confirmAt = 결과 발표(22:00, 매칭 확정) → 신청 후 "결과 안내까지" 카운트다운 기준.
 * matching.pendingResult = 확인 대기 중인 최근 매칭 결과(발표 후~다음 매칭 전, 없으면 null).
 */
public record HomeResponse(
        LocalDateTime now,
        long pointBalance,
        MatchingStatus matching,
        ActiveRoom activeRoom
) {

    public record MatchingStatus(String status, LocalDateTime queuedAt,
                                 LocalDateTime applyDeadlineAt, LocalDateTime confirmAt,
                                 PendingMatchResultResponse pendingResult) {}

    public record ActiveRoom(
            Long roomId,
            String status,
            int currentDay,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {}

    public static HomeResponse of(LocalDateTime now, long pointBalance, QueueStatusResponse queue,
                                  RoomResponse room, PendingMatchResultResponse pendingResult,
                                  LocalDateTime applyDeadlineAt, LocalDateTime confirmAt, int dayStartHour) {
        MatchingStatus matching = new MatchingStatus(queue.status(), queue.queuedAt(),
                applyDeadlineAt, confirmAt, pendingResult);
        ActiveRoom activeRoom = room == null ? null : new ActiveRoom(
                room.roomId(),
                room.status(),
                room.currentDay(),
                room.scheduledDate().atTime(dayStartHour, 0),
                room.scheduledDate().plusDays(room.totalDays()).atTime(dayStartHour, 0)
        );
        return new HomeResponse(now, pointBalance, matching, activeRoom);
    }
}
