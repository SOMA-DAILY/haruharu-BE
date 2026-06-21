package org.son.haruharu.domain.value.implement;

import java.util.UUID;

public record UserValueAnswer(
        UUID id,
        UUID userId,
        UUID questionId,
        String answer
) {}
