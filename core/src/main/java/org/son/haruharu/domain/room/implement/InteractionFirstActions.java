package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;

public record InteractionFirstActions(Long roomMemberId, LocalDateTime firstReactedAt,
        LocalDateTime firstVideoRepliedAt) {
    public static InteractionFirstActions empty(Long memberId) {
        return new InteractionFirstActions(memberId, null, null);
    }

    public InteractionFirstActions record(InteractionGuideKind kind, LocalDateTime at) {
        return new InteractionFirstActions(roomMemberId,
                kind == InteractionGuideKind.REACTION && firstReactedAt == null ? at : firstReactedAt,
                kind == InteractionGuideKind.REPLY && firstVideoRepliedAt == null ? at : firstVideoRepliedAt);
    }
}
