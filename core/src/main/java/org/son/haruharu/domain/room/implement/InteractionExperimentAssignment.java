package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;

public record InteractionExperimentAssignment(Long roomId, String experimentKey,
        InteractionExperimentVariant variant, LocalDateTime assignedAt) {}
