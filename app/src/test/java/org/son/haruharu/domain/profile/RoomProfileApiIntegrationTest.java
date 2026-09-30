package org.son.haruharu.domain.profile;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomProfileApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired UserProfileJpaRepository userProfileJpaRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock;

    Long userId;
    int birthYear;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_images");
        jdbcTemplate.update("DELETE FROM user_profiles");
        jdbcTemplate.update("DELETE FROM users");
        userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        userProfileJpaRepository.save(UserProfileEntity.from(new UserProfile(null, userId, "서울", "직장인", 175,
                "INFP", "테스터", DrinkingStatus.SOMETIMES, SmokingStatus.NONE, null)));
        birthYear = LocalDate.now(clock).getYear() - 23;
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("아직 안 쓴 유저는 빈 값과 completed=false, 기존 mbti 칸은 그대로 보인다")
    void givenNoRoomProfile_whenGet_thenIncomplete() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/room-profile").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birth_year").isEmpty())
                .andExpect(jsonPath("$.data.age").isEmpty())
                .andExpect(jsonPath("$.data.mbti").value("INFP"))
                .andExpect(jsonPath("$.data.completed").value(false));
    }

    @Test
    @DisplayName("PUT 은 4칸을 저장하고 GET 과 같은 모양으로 답하며, 기존 프로필 PUT 뒤에도 3칸이 남는다")
    void givenValidBody_whenPut_thenSavedAndKeptAfterLegacyProfilePut() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/room-profile").with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birth_year": %d, "mbti": "enfp", "department": " 전자공학과 ", "into_now": "러닝"}
                                """.formatted(birthYear)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birth_year").value(birthYear))
                .andExpect(jsonPath("$.data.age").value(23))
                .andExpect(jsonPath("$.data.mbti").value("ENFP"))
                .andExpect(jsonPath("$.data.department").value("전자공학과"))
                .andExpect(jsonPath("$.data.into_now").value("러닝"))
                .andExpect(jsonPath("$.data.completed").value(true));

        // 지금 앱 마이페이지 저장(기존 API, 새 칸 모름)
        mockMvc.perform(put("/api/v1/users/me/profile").with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"location": "부산", "affiliation": "학생", "height": 170, "mbti": "ENFP", "nickname": "테스터"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/room-profile").with(authentication(auth())))
                .andExpect(jsonPath("$.data.birth_year").value(birthYear))
                .andExpect(jsonPath("$.data.department").value("전자공학과"))
                .andExpect(jsonPath("$.data.into_now").value("러닝"))
                .andExpect(jsonPath("$.data.completed").value(true));
    }

    @Test
    @DisplayName("틀린 칸은 400 VALIDATION_ERROR 와 error.fields 로 칸별 메시지를 내린다")
    void givenInvalidBody_whenPut_thenFieldErrors() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/room-profile").with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"birth_year": %d, "mbti": "ENFX", "department": "가나다라마바사아자차카타파하가나", "into_now": "러닝"}
                                """.formatted(birthYear)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("MBTI를 다시 골라주세요"))
                .andExpect(jsonPath("$.error.fields[0].field").value("mbti"))
                .andExpect(jsonPath("$.error.fields[1].field").value("department"))
                .andExpect(jsonPath("$.error.fields[1].message").value("학과는 15자까지 쓸 수 있어요"));
    }

    @Test
    @DisplayName("다른 400 응답에는 error.fields 키가 붙지 않는다")
    void givenLegacyValidationError_whenPutProfile_thenNoFieldsKey() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/profile").with(authentication(auth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fields").doesNotExist());
    }
}
