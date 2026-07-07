package org.son.haruharu.domain.user;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserImageEntity;
import org.son.haruharu.domain.profile.storage.UserImageJpaRepository;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.profile.storage.UserProfileJpaRepository;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.domain.value.implement.UserValueAnswer;
import org.son.haruharu.domain.value.implement.ValueQuestion;
import org.son.haruharu.domain.value.storage.UserValueAnswerEntity;
import org.son.haruharu.domain.value.storage.UserValueAnswerJpaRepository;
import org.son.haruharu.domain.value.storage.ValueQuestionEntity;
import org.son.haruharu.domain.value.storage.ValueQuestionJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired UserProfileJpaRepository userProfileJpaRepository;
    @Autowired UserImageJpaRepository userImageJpaRepository;
    @Autowired ValueQuestionJpaRepository valueQuestionJpaRepository;
    @Autowired UserValueAnswerJpaRepository userValueAnswerJpaRepository;

    @BeforeEach
    void clean() {
        userValueAnswerJpaRepository.deleteAll();
        valueQuestionJpaRepository.deleteAll();
        userImageJpaRepository.deleteAll();
        userProfileJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private Long saveUser(UserStatus status) {
        return userJpaRepository.save(UserEntity.from(new User(null, status, null))).toDomain().id();
    }

    private void saveProfileWithNickname(Long userId) {
        userProfileJpaRepository.save(UserProfileEntity.from(
                new UserProfile(null, userId, "서울", "직장인", 175, "INFP", "테스터", "가끔", "비흡연")));
    }

    private void saveImage(Long userId) {
        userImageJpaRepository.save(UserImageEntity.from(new UserImage(null, userId, "https://cdn/img.png", 0)));
    }

    private void saveValueAnswer(Long userId) {
        Long questionId = valueQuestionJpaRepository.save(ValueQuestionEntity.from(
                new ValueQuestion(null, "질문", "A", "B", "LIFESTYLE", 1, true))).toDomain().id();
        userValueAnswerJpaRepository.save(UserValueAnswerEntity.from(
                new UserValueAnswer(null, userId, questionId, "A")));
    }

    @Test
    @DisplayName("프로필·가치관·이미지를 모두 갖춘 ONBOARDING 유저가 온보딩 완료 시 200·ACTIVE 반환 후 DB 상태가 ACTIVE가 된다")
    void givenCompletedData_whenCompleteOnboarding_thenReturnsActiveAndPersists() throws Exception {
        Long userId = saveUser(UserStatus.ONBOARDING);
        saveProfileWithNickname(userId);
        saveImage(userId);
        saveValueAnswer(userId);

        mockMvc.perform(post("/api/v1/users/me/onboarding/complete")
                        .with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.user_status").value("ACTIVE"));

        assertThat(userJpaRepository.findById(userId).orElseThrow().toDomain().status())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("이미지가 없는 ONBOARDING 유저가 온보딩 완료 시 400을 반환하고 상태가 전이되지 않는다")
    void givenNoImage_whenCompleteOnboarding_thenReturns400AndKeepsOnboarding() throws Exception {
        Long userId = saveUser(UserStatus.ONBOARDING);
        saveProfileWithNickname(userId);
        saveValueAnswer(userId);

        mockMvc.perform(post("/api/v1/users/me/onboarding/complete")
                        .with(authentication(auth(userId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));

        assertThat(userJpaRepository.findById(userId).orElseThrow().toDomain().status())
                .isEqualTo(UserStatus.ONBOARDING);
    }
}
