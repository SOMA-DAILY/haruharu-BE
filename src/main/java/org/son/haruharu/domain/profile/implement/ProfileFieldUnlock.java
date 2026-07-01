package org.son.haruharu.domain.profile.implement;

import java.time.LocalDateTime;
import java.util.UUID;

/** 프로필 항목 해금 기록. 가치관 답변 해금(answerId=대상 특정)과 달리 대상·항목을 함께 키로 갖는다. */
public record ProfileFieldUnlock(
        UUID id,
        UUID viewerRoomMemberId,
        UUID targetRoomMemberId,
        String fieldKey,
        LocalDateTime unlockedAt
) {
    public static ProfileFieldUnlock of(UUID viewerRoomMemberId, UUID targetRoomMemberId, String fieldKey,
                                        LocalDateTime unlockedAt) {
        return new ProfileFieldUnlock(null, viewerRoomMemberId, targetRoomMemberId, fieldKey, unlockedAt);
    }
}
