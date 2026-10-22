package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;

/** 방에서 성공한 최초 행동과 안내 소비는 서로 다른 상태다. 취소·삭제로 최초 성공을 되돌리지 않는다. */
public record InteractionGuidanceProgress(
        LocalDateTime firstReactedAt,
        LocalDateTime firstVideoRepliedAt,
        LocalDateTime reactionGuideConsumedAt,
        LocalDateTime replyGuideConsumedAt
) {
    public static InteractionGuidanceProgress empty() {
        return new InteractionGuidanceProgress(null, null, null, null);
    }

    public InteractionGuideKind nextGuide() {
        if (firstReactedAt == null && reactionGuideConsumedAt == null) return InteractionGuideKind.REACTION;
        if (firstVideoRepliedAt == null && replyGuideConsumedAt == null) return InteractionGuideKind.REPLY;
        return null;
    }

    public boolean completed(InteractionGuideKind kind) {
        return kind == InteractionGuideKind.REACTION ? firstReactedAt != null : firstVideoRepliedAt != null;
    }
}
