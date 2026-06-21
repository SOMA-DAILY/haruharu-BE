package org.son.haruharu.domain.auth.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.auth.storage.exception.RefreshTokenNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenReader {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshToken getByTokenHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(RefreshTokenNotFoundException::new);
    }

    public java.util.Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }
}
