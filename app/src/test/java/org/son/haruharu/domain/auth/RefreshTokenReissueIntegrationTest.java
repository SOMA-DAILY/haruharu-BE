package org.son.haruharu.domain.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.auth.implement.RefreshToken;
import org.son.haruharu.domain.auth.implement.RefreshTokenWriter;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.son.haruharu.utils.TokenHashUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 토큰 재발급 경로를 실제 HTTP + DB(UNIQUE 제약)로 검증한다(#417).
 * 단위 테스트는 JwtProvider 를 mock 하므로 "같은 초 발급분이 token_hash UNIQUE 에 충돌해 409" 를 못 잡는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenReissueIntegrationTest {

    private static final String REFRESH_URL = "/api/v1/auth/token/refresh";

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired RefreshTokenWriter refreshTokenWriter;
    @Autowired JwtProvider jwtProvider;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
    }

    private String issueStoredRefreshToken() {
        return issueStoredRefreshToken(null);
    }

    private String issueStoredRefreshToken(LocalDateTime revokedAt) {
        String token = jwtProvider.generateRefreshToken(userId);
        refreshTokenWriter.save(new RefreshToken(null, userId, TokenHashUtils.sha256Hex(token),
            LocalDateTime.now().plusDays(30), revokedAt));
        return token;
    }

    private MvcResult refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post(REFRESH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refresh_token\":\"" + refreshToken + "\"}"))
            .andReturn();
    }

    private String refreshTokenOf(MvcResult result) throws Exception {
        Matcher m = Pattern.compile("\"refresh_token\":\"([^\"]+)\"").matcher(result.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    @DisplayName("같은 유저가 같은 초에 두 번 재발급해도 두 요청 모두 200이고 발급 토큰은 서로 다르다 — 409 UNIQUE 충돌 회귀 방지")
    void givenSameUserSameSecond_whenRefreshTwice_thenBothSucceedWithDistinctTokens() throws Exception {
        String tokenA = issueStoredRefreshToken();
        String tokenB = issueStoredRefreshToken(); // jti 없던 시절엔 여기서 이미 tokenA 와 동일해 저장이 UNIQUE 충돌

        MvcResult first = refresh(tokenA);
        MvcResult second = refresh(tokenB);

        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(refreshTokenOf(first)).isNotEqualTo(refreshTokenOf(second));
    }

    @Test
    @DisplayName("재발급 직후 같은 토큰을 다시 제시하면 401이지만 방금 발급된 새 토큰은 살아 있다 — 병렬 재발급이 세션을 파괴하지 않는다")
    void givenJustRotatedToken_whenPresentedAgain_thenUnauthorizedButNewSessionSurvives() throws Exception {
        String original = issueStoredRefreshToken();

        MvcResult first = refresh(original);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        String rotated = refreshTokenOf(first);

        // 늦게 도착한 병렬 요청: 방금 revoke 된 토큰을 재제시
        mockMvc.perform(post(REFRESH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refresh_token\":\"" + original + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_REFRESH_TOKEN"));

        // grace 안이므로 전체 폐기가 일어나지 않아 새 토큰은 여전히 유효
        assertThat(refresh(rotated).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("grace 밖에서 revoke된 토큰을 재제시하면 탈취 재사용으로 보고 전체 세션 폐기가 실제로 커밋된다 — 롤백 회귀 방지")
    void givenTokenRevokedLongAgo_whenPresentedAgain_thenAllSessionsRevokedAndPersisted() throws Exception {
        String stolen = issueStoredRefreshToken(LocalDateTime.now().minusMinutes(5));
        String otherDevice = issueStoredRefreshToken();

        assertThat(refresh(stolen).getResponse().getStatus()).isEqualTo(401);

        // 거절 응답(예외)에도 revokeAllByUserId 가 롤백되지 않았어야 다른 기기 세션도 죽는다
        assertThat(refresh(otherDevice).getResponse().getStatus()).isEqualTo(401);
    }
}
