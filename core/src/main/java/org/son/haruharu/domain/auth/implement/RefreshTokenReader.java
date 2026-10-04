package org.son.haruharu.domain.auth.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class RefreshTokenReader {

    private final RefreshTokenRepository refreshTokenRepository;

    public java.util.Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }
}
