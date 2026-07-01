package org.son.haruharu.domain.value.implement;

import java.util.UUID;

public record ValueQuestion(
        UUID id,
        String questionText,
        String category,
        int displayOrder,
        boolean isActive,
        int unlockCost
) {}
