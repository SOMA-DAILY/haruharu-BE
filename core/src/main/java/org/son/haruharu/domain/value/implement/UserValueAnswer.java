package org.son.haruharu.domain.value.implement;

public record UserValueAnswer(
        Long id,
        Long userId,
        Long questionId,
        String answer
) {}
