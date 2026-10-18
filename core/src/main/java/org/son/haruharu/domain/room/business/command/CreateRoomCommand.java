package org.son.haruharu.domain.room.business.command;

import java.time.LocalDateTime;

/**
 * 초대코드 모델 방 생성 입력(#311). 운영진/어드민이 방마다 스케줄 시각을 직접 지정한다.
 * 시각 순서 규약: startsAt &lt; finalSelectionOpensAt &lt; finalSelectionClosesAt ≤ resultAt ≤ endsAt.
 * 기수·이름·성별 정원(#513)은 모두 선택이다(웹 전용 방은 비운다).
 */
public record CreateRoomCommand(
        LocalDateTime startsAt,
        LocalDateTime finalSelectionOpensAt,
        LocalDateTime finalSelectionClosesAt,
        LocalDateTime resultAt,
        LocalDateTime endsAt,
        int totalDays,
        Long cohortId,
        String name,
        Integer maleCapacity,
        Integer femaleCapacity
) {
    /** 편성 정보 없이 스케줄만 주는 생성(#513 이전 모양). */
    public CreateRoomCommand(LocalDateTime startsAt, LocalDateTime finalSelectionOpensAt,
                             LocalDateTime finalSelectionClosesAt, LocalDateTime resultAt,
                             LocalDateTime endsAt, int totalDays) {
        this(startsAt, finalSelectionOpensAt, finalSelectionClosesAt, resultAt, endsAt, totalDays,
                null, null, null, null);
    }
}
