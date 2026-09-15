package org.son.haruharu.domain.auth.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenWriter {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshToken save(RefreshToken refreshToken) {
        return refreshTokenRepository.save(refreshToken);
    }

    public void revokeAllByUserId(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    /** 토큰을 원자적으로 소비한다. 이미 소비된 토큰이면 false (#417). */
    public boolean revokeByTokenHash(String tokenHash) {
        return refreshTokenRepository.revokeByTokenHash(tokenHash);
    }
}
