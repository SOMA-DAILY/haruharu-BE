package org.son.haruharu.domain.auth.implement;

import java.time.LocalDateTime;

public record RefreshToken(
        Long id,
        Long userId,
        String tokenHash,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt
) {
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
