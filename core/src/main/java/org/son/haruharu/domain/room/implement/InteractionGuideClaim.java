package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;

public record InteractionGuideClaim(String id, Long roomMemberId, InteractionGuideKind kind,
        Long videoPostId, String viewerSessionId, LocalDateTime claimedAt) {}
