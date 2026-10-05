package org.son.haruharu.utils;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인증 실패 사유를 호출자에게 전달할 수 있는지 고정한다(#259 2-1).
 * 기존 isValid()는 boolean이라 "만료인지 서명 불일치인지"가 소멸했고, 그 탓에 EntryPoint가 401 사유를 로깅할 수 없었다.
 */
class JwtProviderTest {

    // 테스트 전용 더미 시크릿(HS256 최소 256비트). 실제 운영 시크릿과 무관하다.
    private static final String SECRET = "test-secret-key-for-unit-test-only-32bytes+";
    private static final String OTHER_SECRET = "another-secret-key-for-unit-test-only-32b+";

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, 60_000L, 600_000L);

    @Test
    @DisplayName("정상 발급된 액세스 토큰은 VALID를 반환한다")
    void givenValidToken_whenValidate_thenValid() {
        String token = jwtProvider.generateAccessToken(1L);

        assertThat(jwtProvider.validate(token)).isEqualTo(JwtValidation.VALID);
    }

    @Test
    @DisplayName("만료된 토큰은 EXPIRED를 반환한다")
    void givenExpiredToken_whenValidate_thenExpired() {
        // given — 발급 즉시 만료되도록 음수 만료시간
        JwtProvider expiringProvider = new JwtProvider(SECRET, -1_000L, 600_000L);
        String token = expiringProvider.generateAccessToken(1L);

        assertThat(jwtProvider.validate(token)).isEqualTo(JwtValidation.EXPIRED);
    }

    @Test
    @DisplayName("다른 시크릿으로 서명된 토큰은 BAD_SIGNATURE를 반환한다")
    void givenTokenSignedWithOtherSecret_whenValidate_thenBadSignature() {
        // given — ECS/EC2 시크릿 불일치로 전 요청 401이 났던 상황의 재현
        JwtProvider otherProvider = new JwtProvider(OTHER_SECRET, 60_000L, 600_000L);
        String token = otherProvider.generateAccessToken(1L);

        assertThat(jwtProvider.validate(token)).isEqualTo(JwtValidation.BAD_SIGNATURE);
    }

    @Test
    @DisplayName("JWT 형식이 아닌 쓰레기값은 MALFORMED를 반환한다")
    void givenGarbageToken_whenValidate_thenMalformed() {
        assertThat(jwtProvider.validate("this-is-not-a-jwt")).isEqualTo(JwtValidation.MALFORMED);
    }

    @Test
    @DisplayName("빈 토큰은 MALFORMED를 반환한다")
    void givenEmptyToken_whenValidate_thenMalformed() {
        assertThat(jwtProvider.validate("")).isEqualTo(JwtValidation.MALFORMED);
    }

    @Test
    @DisplayName("기존 isValid() 계약은 그대로 유지된다 — core를 공유하는 admin·AuthService 회귀 방지")
    void givenTokens_whenIsValid_thenContractPreserved() {
        JwtProvider otherProvider = new JwtProvider(OTHER_SECRET, 60_000L, 600_000L);

        assertThat(jwtProvider.isValid(jwtProvider.generateAccessToken(1L))).isTrue();
        assertThat(jwtProvider.isValid(otherProvider.generateAccessToken(1L))).isFalse();
        assertThat(jwtProvider.isValid("garbage")).isFalse();
    }

    @Test
    @DisplayName("같은 유저에게 연속 발급한 리프레시 토큰은 같은 초에 만들어져도 서로 다르다(#417)")
    void givenSameUserSameSecond_whenGenerateRefreshTwice_thenTokensDiffer() {
        // given/when — iat·exp가 초 단위라 같은 초 발급분은 jti 없이는 바이트 동일했다
        String first = jwtProvider.generateRefreshToken(1L);
        String second = jwtProvider.generateRefreshToken(1L);

        assertThat(first).isNotEqualTo(second);
        assertThat(jwtProvider.isValid(first)).isTrue();
        assertThat(jwtProvider.isValid(second)).isTrue();
        // 초 경계를 넘어 우연히 달라진 게 아니라 jti 가 있어서 다른 것임을 고정한다
        assertThat(jtiOf(first)).isNotBlank().isNotEqualTo(jtiOf(second));
    }

    private static String jtiOf(String token) {
        return Jwts.parser()
            .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .build().parseSignedClaims(token).getPayload().getId();
    }

    @Test
    @DisplayName("같은 유저에게 연속 발급한 액세스 토큰도 서로 다르다(#417)")
    void givenSameUserSameSecond_whenGenerateAccessTwice_thenTokensDiffer() {
        String first = jwtProvider.generateAccessToken(1L);
        String second = jwtProvider.generateAccessToken(1L);

        assertThat(first).isNotEqualTo(second);
        assertThat(jtiOf(first)).isNotBlank().isNotEqualTo(jtiOf(second));
    }
}
