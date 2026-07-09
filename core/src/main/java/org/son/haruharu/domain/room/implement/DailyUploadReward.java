package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;

/**
 * 일일 업로드 완주 보상 지급 기록. (roomMemberId, dayNumber)당 1행으로 멱등 지급을 보장한다(append-only).
 * 재촬영 삭제/재업로드로 완주가 반복돼도 재지급하지 않으며, 이미 지급한 보상을 회수하지도 않는다.
 */
public record DailyUploadReward(
        Long id,
        Long roomMemberId,
        int dayNumber,
        long amount,
        LocalDateTime grantedAt
) {
    public static DailyUploadReward of(Long roomMemberId, int dayNumber, long amount, LocalDateTime grantedAt) {
        return new DailyUploadReward(null, roomMemberId, dayNumber, amount, grantedAt);
    }
}
