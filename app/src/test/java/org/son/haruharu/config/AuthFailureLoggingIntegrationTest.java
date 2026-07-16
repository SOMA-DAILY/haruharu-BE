package org.son.haruharu.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.support.LogCapture;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증·인가 실패가 로그에 남는지 고정한다(#259 2-1).
 * 401은 SecurityConfig의 EntryPoint가, 403은 JwtAuthenticationFilter가 직접 응답을 쓰므로
 * 둘 다 GlobalExceptionHandler에 도달하지 않아 로그가 한 줄도 없었다(OWASP A09).
 *
 * 응답 바디는 현행 그대로 뭉뚱그린다 — 실패 사유 노출은 계정 열거에 악용된다. 사유는 로그에만 남긴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFailureLoggingIntegrationTest {

    private static final String PROTECTED_PROBE = "/api/v1/matching/__filter_probe";
    // 테스트 전용 더미 시크릿. 운영 시크릿과 무관하며, ECS/EC2 시크릿 드리프트로 전 요청 401이 났던 상황의 재현용.
    private static final String OTHER_SECRET = "another-secret-key-for-integration-test-32b+";

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired JwtProvider jwtProvider;
    // 만료 토큰은 같은 시크릿으로 서명해야 BAD_SIGNATURE가 아닌 EXPIRED로 갈린다.
    @Value("${jwt.secret}") String jwtSecret;

    private LogCapture logs;

    @BeforeEach
    void setUp() {
        userJpaRepository.deleteAll();
        // 401은 EntryPoint(SecurityConfig), 403은 JwtAuthenticationFilter가 남긴다
        logs = LogCapture.of(SecurityConfig.class, JwtAuthenticationFilter.class);
    }

    @AfterEach
    void tearDown() {
        logs.close();
    }

    private String tokenForUser(UserStatus status) {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, status, null))).toDomain().id();
        return jwtProvider.generateAccessToken(userId);
    }

    private List<ILoggingEvent> warnings() {
        return logs.at(Level.WARN);
    }

    private String warningText() {
        return logs.text();
    }

    @Test
    @DisplayName("서명이 깨진 토큰으로 접근 시 401 응답은 그대로고 로그에 서명 실패 사유가 남는다")
    void givenBadSignatureToken_whenAccessProtected_thenLogsReasonWithoutToken() throws Exception {
        // given — 다른 시크릿으로 서명한 토큰
        String forgedToken = new JwtProvider(OTHER_SECRET, 60_000L, 600_000L).generateAccessToken(1L);

        // when
        mockMvc.perform(get(PROTECTED_PROBE).header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));

        // then — 사유는 로그에만, 토큰 원문은 절대 남기지 않는다
        assertThat(warnings()).hasSize(1);
        assertThat(warningText()).contains("BAD_SIGNATURE");
        assertThat(warningText()).doesNotContain(forgedToken);
    }

    @Test
    @DisplayName("토큰 없이 접근 시 401 로그에 토큰 없음 사유가 남는다")
    void givenNoToken_whenAccessProtected_thenLogsNoTokenReason() throws Exception {
        mockMvc.perform(get(PROTECTED_PROBE))
                .andExpect(status().isUnauthorized());

        assertThat(warnings()).hasSize(1);
        assertThat(warningText()).contains("NO_TOKEN");
    }

    @Test
    @DisplayName("쓰레기값 토큰으로 접근 시 401 로그에 형식 오류 사유가 남는다")
    void givenGarbageToken_whenAccessProtected_thenLogsMalformedReason() throws Exception {
        mockMvc.perform(get(PROTECTED_PROBE).header("Authorization", "Bearer this-is-not-a-jwt"))
                .andExpect(status().isUnauthorized());

        assertThat(warnings()).hasSize(1);
        assertThat(warningText()).contains("MALFORMED");
        assertThat(warningText()).doesNotContain("this-is-not-a-jwt");
    }

    @Test
    @DisplayName("만료된 토큰으로 접근 시 401 로그에 만료 사유가 남는다")
    void givenExpiredToken_whenAccessProtected_thenLogsExpiredReason() throws Exception {
        // given — 발급 즉시 만료(음수 만료시간). 서명은 정상이라 만료로만 갈린다.
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        String expiredToken = new JwtProvider(jwtSecret, -1_000L, 600_000L).generateAccessToken(userId);

        mockMvc.perform(get(PROTECTED_PROBE).header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());

        assertThat(warningText()).contains("EXPIRED");
    }

    @Test
    @DisplayName("BANNED 유저 접근 시 403 로그에 code와 userId가 남는다")
    void givenBannedUser_whenAccessProtected_thenLogsUserInactiveWithUserId() throws Exception {
        String token = tokenForUser(UserStatus.BANNED);
        Long userId = jwtProvider.extractUserId(token);

        mockMvc.perform(get(PROTECTED_PROBE).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("USER_INACTIVE"));

        assertThat(warnings()).hasSize(1);
        assertThat(warningText()).contains("USER_INACTIVE");
        assertThat(warningText()).contains(String.valueOf(userId));
        assertThat(warningText()).contains(PROTECTED_PROBE);
    }

    @Test
    @DisplayName("ONBOARDING 유저가 멤버 전용 경로 접근 시 403 로그에 code와 userId가 남는다")
    void givenOnboardingUser_whenAccessMemberOnly_thenLogsOnboardingRequiredWithUserId() throws Exception {
        String token = tokenForUser(UserStatus.ONBOARDING);
        Long userId = jwtProvider.extractUserId(token);

        mockMvc.perform(get(PROTECTED_PROBE).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_REQUIRED"));

        assertThat(warnings()).hasSize(1);
        assertThat(warningText()).contains("ONBOARDING_REQUIRED");
        assertThat(warningText()).contains(String.valueOf(userId));
    }

    @Test
    @DisplayName("인증에 성공한 요청은 인증 실패 로그를 남기지 않는다")
    void givenActiveUser_whenAccessProtected_thenNoAuthFailureLog() throws Exception {
        mockMvc.perform(get(PROTECTED_PROBE).header("Authorization", "Bearer " + tokenForUser(UserStatus.ACTIVE)))
                .andExpect(status().isNotFound());

        assertThat(warnings()).isEmpty();
    }

}
