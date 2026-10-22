package org.son.haruharu.domain.room.business.dto;

import java.time.LocalDateTime;
import org.son.haruharu.domain.room.implement.InteractionExperimentVariant;
import org.son.haruharu.domain.room.implement.InteractionGuideKind;

public record InteractionGuidanceResponse(String experimentKey, InteractionExperimentVariant variant,
        LocalDateTime assignedAt, LocalDateTime now, LocalDateTime eligibleAt,
        LocalDateTime firstReactedAt, LocalDateTime firstVideoRepliedAt,
        LocalDateTime reactionGuideConsumedAt, LocalDateTime replyGuideConsumedAt,
        InteractionGuideKind nextGuide) {}
