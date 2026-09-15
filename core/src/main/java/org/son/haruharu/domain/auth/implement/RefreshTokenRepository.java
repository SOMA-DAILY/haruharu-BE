package org.son.haruharu.domain.auth.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken refreshToken);

    void revokeAllByUserId(Long userId);

    /** 살아 있는 토큰을 revoke 했으면 true, 이미 revoke 됐거나 없으면 false (원자적 소비, #417). */
    boolean revokeByTokenHash(String tokenHash);

    List<Long> findUserIdsLastActiveBetween(LocalDateTime from, LocalDateTime to);
}
