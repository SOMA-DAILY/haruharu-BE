package org.son.haruharu.domain.auth.implement;

import java.time.LocalDateTime;
import java.util.UUID;

public record RefreshToken(
        UUID id,
        UUID userId,
        String tokenHash,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt
) {}
