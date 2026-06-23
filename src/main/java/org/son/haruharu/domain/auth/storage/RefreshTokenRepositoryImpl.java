package org.son.haruharu.domain.auth.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.auth.implement.RefreshToken;
import org.son.haruharu.domain.auth.implement.RefreshTokenRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

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
    public void revokeAllByUserId(UUID userId) {
        refreshTokenJpaRepository.deleteByUserId(userId);
    }

    @Override
    public void revokeByTokenHash(String tokenHash) {
        refreshTokenJpaRepository.findByTokenHash(tokenHash)
            .ifPresent(entity -> {
                entity.revoke();
                refreshTokenJpaRepository.save(entity);
            });
    }
}
