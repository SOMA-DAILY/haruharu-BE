package org.son.haruharu.domain.auth.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.auth.implement.RefreshToken;
import org.son.haruharu.domain.auth.implement.RefreshTokenRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return refreshTokenJpaRepository.findByTokenHash(tokenHash)
            .map(RefreshTokenEntity::toDomain);
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return refreshTokenJpaRepository.save(RefreshTokenEntity.from(refreshToken)).toDomain();
    }

    @Override
    public void revokeAllByUserId(Long userId) {
        refreshTokenJpaRepository.deleteByUserId(userId);
    }

    /** revokedAt 은 시스템 시각 기준(RefreshToken.isExpired 와 동일). */
    @Override
    public boolean revokeByTokenHash(String tokenHash) {
        return refreshTokenJpaRepository.revokeIfActive(tokenHash, LocalDateTime.now()) == 1;
    }
}
