package org.son.haruharu.domain.home.business.dto;

import java.time.LocalDateTime;

import org.son.haruharu.domain.room.business.dto.PendingMatchResultResponse;
import org.son.haruharu.domain.room.business.dto.RoomResponse;

/**
 * 메인 홈 집계 응답(초대코드 모델). 유저 상태는 세 가지: 참여 방 있음(activeRoom)·발표 후 결과 있음(pendingResult)·
 * 둘 다 없음(activeRoom null → 클라가 초대코드 입력 화면 표시).
 * now = 응답 생성 시점 기준 시각(주입 Clock, dev 가상 시계 반영) → 클라 카운트다운의 기기 시계 오차 보정 기준.
 * (자동매칭 큐·코인 잔액은 새 MVP에서 제거 — coin 재도입 시 필드 복구, 자동매칭은 초대코드 모델로 대체·영구 제거)
 */
public record HomeResponse(
        LocalDateTime now,
        ActiveRoom activeRoom,
        PendingMatchResultResponse pendingResult
) {

    public record ActiveRoom(
            Long roomId,
            String status,
            int currentDay,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {}

    public static HomeResponse of(LocalDateTime now, RoomResponse room, PendingMatchResultResponse pendingResult) {
        ActiveRoom activeRoom = room == null ? null : new ActiveRoom(
                room.roomId(), room.status(), room.currentDay(), room.startsAt(), room.endsAt());
        return new HomeResponse(now, activeRoom, pendingResult);
    }
}
