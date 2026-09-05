package org.son.haruharu.domain.user;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.son.haruharu.domain.profile.storage.UserProfileJpaRepository;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.profile.implement.Gender;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired UserProfileJpaRepository userProfileJpaRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        // BaseEntity @SoftDelete 전역 필터로 deleteAll()이 soft-delete가 되어 물리 행이 남는다.
        // 비트랜잭션 테스트의 격리는 native 물리삭제로 복원한다.
        jdbcTemplate.update("DELETE FROM user_value_answers");
        jdbcTemplate.update("DELETE FROM value_questions");
        jdbcTemplate.update("DELETE FROM user_images");
        jdbcTemplate.update("DELETE FROM user_profiles");
        jdbcTemplate.update("DELETE FROM users");
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private Long saveUser(UserStatus status) {
        return userJpaRepository.save(UserEntity.from(new User(null, status, null))).toDomain().id();
    }

    @Test
    @DisplayName("닉네임·성별 PUT한 ONBOARDING 유저가 완료 API 호출 시 200·ACTIVE 반환 후 DB 상태가 ACTIVE가 된다")
    void givenNicknameOnly_whenUpdateProfileAndCompleteOnboarding_thenReturnsActiveAndPersists() throws Exception {
        Long userId = saveUser(UserStatus.ONBOARDING);

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .with(authentication(auth(userId)))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nickname\":\"하루\",\"gender\":\"MALE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.nickname").value("하루"));

        mockMvc.perform(post("/api/v1/users/me/onboarding/complete")
                        .with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.user_status").value("ACTIVE"));

        assertThat(userJpaRepository.findById(userId).orElseThrow().toDomain().status())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(userProfileJpaRepository.findByUserId(userId).orElseThrow().toDomain().nickname())
                .isEqualTo("하루");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"nickname\":null}", "{\"nickname\":\"\"}", "{\"nickname\":\"   \"}"})
    @DisplayName("닉네임 누락·null·빈 문자열·공백 PUT은 400이고 완료 API도 ONBOARDING_INCOMPLETE를 반환한다")
    void givenInvalidNickname_whenUpdateProfileAndCompleteOnboarding_thenReturns400AndKeepsOnboarding(String body)
            throws Exception {
        Long userId = saveUser(UserStatus.ONBOARDING);

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .with(authentication(auth(userId)))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(post("/api/v1/users/me/onboarding/complete")
                        .with(authentication(auth(userId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));

        assertThat(userJpaRepository.findById(userId).orElseThrow().toDomain().status())
                .isEqualTo(UserStatus.ONBOARDING);
    }

    @Test
    @DisplayName("성별 없이 프로필 PUT은 200이지만 성별이 없으면 온보딩 완료 시 ONBOARDING_INCOMPLETE(#392: 성별은 완료 게이트 필수)")
    void givenNoGender_whenUpdateProfileThenComplete_thenSavedButOnboardingIncomplete() throws Exception {
        Long userId = saveUser(UserStatus.ONBOARDING);

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .with(authentication(auth(userId)))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nickname\":\"하루\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/me/onboarding/complete")
                        .with(authentication(auth(userId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));

        assertThat(userJpaRepository.findById(userId).orElseThrow().toDomain().status())
                .isEqualTo(UserStatus.ONBOARDING);
    }

    @Test
    @DisplayName("성별을 이미 가진 유저의 편집 PUT은 성별 미전송이어도 200이고 기존 성별을 보존한다(#392 편집 계약 유지)")
    void givenExistingGender_whenEditWithoutGender_thenSucceedsAndPreserves() throws Exception {
        Long userId = saveUser(UserStatus.ACTIVE);
        mockMvc.perform(put("/api/v1/users/me/profile")
                        .with(authentication(auth(userId)))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nickname\":\"하루\",\"gender\":\"FEMALE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .with(authentication(auth(userId)))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nickname\":\"하루하루\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("하루하루"));

        assertThat(userProfileJpaRepository.findByUserId(userId).orElseThrow().toDomain().gender())
                .isEqualTo(Gender.FEMALE);
    }
}
