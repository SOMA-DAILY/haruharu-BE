package org.son.haruharu.domain.user.implement;

import java.time.LocalDateTime;

public record User(
        Long id,
        UserStatus status,
        LocalDateTime createdAt
) {
}
