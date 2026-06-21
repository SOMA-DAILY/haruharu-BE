package org.son.haruharu.domain.user.implement;

import java.time.LocalDateTime;
import java.util.UUID;

public record User(
        UUID id,
        UserStatus status,
        LocalDateTime createdAt
) {}
