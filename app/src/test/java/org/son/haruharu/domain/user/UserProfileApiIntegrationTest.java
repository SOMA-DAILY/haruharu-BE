package org.son.haruharu.domain.user;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.profile.storage.UserProfileJpaRepository;
import org.son.haruharu.domain.user.implement.User;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired UserProfileJpaRepository userProfileJpaRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        // BaseEntity @SoftDelete라 deleteAll이 물리삭제가 아니므로 native로 격리 복원(온보딩 통합테스트와 동일).
        jdbcTemplate.update("DELETE FROM user_images");
        jdbcTemplate.update("DELETE FROM user_profiles");
        jdbcTemplate.update("DELETE FROM users");
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private Long saveUser(boolean demoMode) {
        return userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null, demoMode)))
                .toDomain().id();
    }

    private void saveProfile(Long userId) {
        userProfileJpaRepository.save(UserProfileEntity.from(
                new UserProfile(null, userId, "서울", "직장인", 175, "INFP", "테스터",
                        DrinkingStatus.SOMETIMES, SmokingStatus.NONE)));
    }

    @Test
    @DisplayName("데모 계정이 내 정보 조회 시 demo_mode가 true로 반환된다")
    void givenDemoUser_whenGetMyProfile_thenDemoModeTrue() throws Exception {
        Long userId = saveUser(true);
        saveProfile(userId);

        mockMvc.perform(get("/api/v1/users/me").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.demo_mode").value(true));
    }

    @Test
    @DisplayName("일반 계정이 내 정보 조회 시 demo_mode가 false로 반환된다")
    void givenNormalUser_whenGetMyProfile_thenDemoModeFalse() throws Exception {
        Long userId = saveUser(false);
        saveProfile(userId);

        mockMvc.perform(get("/api/v1/users/me").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.demo_mode").value(false));
    }
}
