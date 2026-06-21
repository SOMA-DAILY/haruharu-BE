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
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void revokeAllByUserId(UUID userId) {
        // TODO
    }
}
