package org.son.haruharu.domain.room.business.command;

import java.time.LocalDateTime;

/**
 * 초대코드 모델 방 생성 입력(#311). 운영진/어드민이 방마다 스케줄 시각을 직접 지정한다.
 * 시각 순서 규약: startsAt &lt; finalSelectionOpensAt &lt; finalSelectionClosesAt ≤ resultAt ≤ endsAt.
 */
public record CreateRoomCommand(
        LocalDateTime startsAt,
        LocalDateTime finalSelectionOpensAt,
        LocalDateTime finalSelectionClosesAt,
        LocalDateTime resultAt,
        LocalDateTime endsAt,
        int totalDays,
        boolean isTest
) {
    public CreateRoomCommand(LocalDateTime startsAt, LocalDateTime finalSelectionOpensAt,
                             LocalDateTime finalSelectionClosesAt, LocalDateTime resultAt,
                             LocalDateTime endsAt, int totalDays) {
        this(startsAt, finalSelectionOpensAt, finalSelectionClosesAt, resultAt, endsAt, totalDays, false);
    }
}
