package org.son.haruharu.domain.auth.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken refreshToken);

    void revokeAllByUserId(Long userId);

    void revokeByTokenHash(String tokenHash);

    List<Long> findUserIdsLastActiveBetween(LocalDateTime from, LocalDateTime to);
}
