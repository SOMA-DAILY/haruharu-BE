package org.son.haruharu.domain.auth.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RefreshTokenReader {

    private final RefreshTokenRepository refreshTokenRepository;

    public java.util.Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }

    /** 마지막 접속(토큰 발급)이 [from, to) 구간인 유저 — 잠수 판단용. */
    public List<Long> findInactiveUserIds(LocalDateTime from, LocalDateTime to) {
        return refreshTokenRepository.findUserIdsLastActiveBetween(from, to);
    }
}
