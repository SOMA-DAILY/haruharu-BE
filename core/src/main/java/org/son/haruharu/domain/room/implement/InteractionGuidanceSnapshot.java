package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;

public record InteractionGuidanceSnapshot(String experimentKey, InteractionExperimentVariant variant,
        LocalDateTime assignedAt, LocalDateTime now, LocalDateTime eligibleAt,
        InteractionGuidanceProgress progress, InteractionGuideKind nextGuide) {}
