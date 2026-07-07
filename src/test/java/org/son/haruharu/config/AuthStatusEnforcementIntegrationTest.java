package org.son.haruharu.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthStatusEnforcementIntegrationTest {

    // 매칭 base 하위의 미매핑 경로. 필터가 통과시키면 핸들러 없음으로 404, 필터가 막으면 403.
    private static final String MEMBER_ONLY_PROBE = "/api/v1/matching/__filter_probe";

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired JwtProvider jwtProvider;

    @BeforeEach
    void clean() {
        userJpaRepository.deleteAll();
    }

    private String tokenForUser(UserStatus status) {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, status, null))).toDomain().id();
        return jwtProvider.generateAccessToken(userId);
    }

    @Test
    @DisplayName("차단(BANNED) 유저 토큰으로 보호 엔드포인트 접근 시 403 USER_INACTIVE로 거부된다")
    void givenBannedUserToken_whenAccessProtected_thenForbidden() throws Exception {
        mockMvc.perform(get(MEMBER_ONLY_PROBE).header("Authorization", "Bearer " + tokenForUser(UserStatus.BANNED)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_INACTIVE"));
    }

    @Test
    @DisplayName("온보딩 미완(ONBOARDING) 유저가 멤버 전용 엔드포인트 접근 시 403 ONBOARDING_REQUIRED로 거부된다")
    void givenOnboardingUserToken_whenAccessMemberOnly_thenForbidden() throws Exception {
        mockMvc.perform(get(MEMBER_ONLY_PROBE).header("Authorization", "Bearer " + tokenForUser(UserStatus.ONBOARDING)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_REQUIRED"));
    }

    @Test
    @DisplayName("온보딩 미완 유저도 온보딩 허용 경로(value-questions)는 접근할 수 있다")
    void givenOnboardingUserToken_whenAccessOnboardingPath_thenAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/value-questions").header("Authorization", "Bearer " + tokenForUser(UserStatus.ONBOARDING)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));
    }

    @Test
    @DisplayName("ACTIVE 유저는 멤버 전용 경로에서 상태 필터를 통과한다(핸들러 없음 → 404, 403 아님)")
    void givenActiveUserToken_whenAccessMemberOnly_thenPassesFilter() throws Exception {
        mockMvc.perform(get(MEMBER_ONLY_PROBE).header("Authorization", "Bearer " + tokenForUser(UserStatus.ACTIVE)))
                .andExpect(status().isNotFound());
    }
}
