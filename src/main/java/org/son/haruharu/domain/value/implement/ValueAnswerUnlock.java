package org.son.haruharu.domain.value.implement;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 가치관 답변 해금 기록. roomMemberId(해금한 사람)가 열쇠를 써서
 * userValueAnswerId(상대의 답변)를 해금한 사실을 방 단위로 추적한다.
 */
public record ValueAnswerUnlock(
        UUID id,
        UUID roomMemberId,
        UUID userValueAnswerId,
        LocalDateTime unlockedAt
) {
    public static ValueAnswerUnlock of(UUID roomMemberId, UUID userValueAnswerId, LocalDateTime unlockedAt) {
        return new ValueAnswerUnlock(null, roomMemberId, userValueAnswerId, unlockedAt);
    }
}
