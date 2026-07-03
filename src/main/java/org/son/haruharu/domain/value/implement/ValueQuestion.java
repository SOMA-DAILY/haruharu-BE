package org.son.haruharu.domain.value.implement;

public record ValueQuestion(
        Long id,
        String questionText,
        String category,
        int displayOrder,
        boolean isActive
) {}
