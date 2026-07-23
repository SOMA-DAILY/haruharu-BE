package org.son.haruharu.demo;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 데모 리셋(#267) end-to-end. 제자리 리셋이므로 방·멤버·가상계정은 유지되고 Day1·피드·구름만 되돌아간다.
 * 리셋을 두 번 호출해도 DEMO_GRANT 원장이 1건으로 유지되는 것으로 "원장 하드삭제 후 재지급"(누적 방지)을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoResetApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired RoomJpaRepository roomJpaRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM coin_ledger");
        jdbcTemplate.update("DELETE FROM coin_wallet");
        jdbcTemplate.update("DELETE FROM video_posts");
        jdbcTemplate.update("DELETE FROM room_members");
        jdbcTemplate.update("DELETE FROM rooms");
        jdbcTemplate.update("DELETE FROM user_images");
        jdbcTemplate.update("DELETE FROM user_profiles");
        jdbcTemplate.update("DELETE FROM users");
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private Long saveDemoUser() {
        return userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null, true)))
                .toDomain().id();
    }

    private Long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    @Test
    @DisplayName("데모 리셋: Day2·피드50 상태를 Day1·피드25·구름200으로 되돌리고 방·가상계정은 유지한다")
    void givenAdvancedDemo_whenReset_thenRestoredInPlace() throws Exception {
        Long userId = saveDemoUser();

        // 방 생성(Day1, 가상5명×5=25개) → Day2 진행(+25개, demoDay=2). 데모 계정 본인은 시드 안 함(#282).
        mockMvc.perform(post("/api/v1/demo/room").with(authentication(auth(userId)))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/demo/room/advance-day").with(authentication(auth(userId)))).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM video_posts")).isEqualTo(50L);

        // 1차 리셋 → Day1 복귀, 피드 재시드(가상만 25), 구름 리시드(DEMO_GRANT 1건)
        mockMvc.perform(post("/api/v1/demo/reset").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.day").value(1))
                .andExpect(jsonPath("$.data.room_id").isNumber());

        Room room = roomJpaRepository.findAll().get(0).toDomain();
        assertThat(room.demo()).isTrue();
        assertThat(room.demoDay()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM video_posts")).isEqualTo(25L);
        // 방·멤버·가상계정은 하드삭제하지 않고 재사용
        assertThat(count("SELECT count(*) FROM room_members")).isEqualTo(6L);
        assertThat(count("SELECT count(*) FROM user_profiles")).isEqualTo(5L);
        assertThat(count("SELECT count(*) FROM user_images")).isEqualTo(5L);
        assertThat(count("SELECT count(*) FROM coin_ledger WHERE user_id = ? AND reason = 'DEMO_GRANT'", userId))
                .isEqualTo(1L);

        // 2차 리셋 → 원장 하드삭제 후 재지급이라 DEMO_GRANT는 여전히 1건(누적 안 됨), 피드도 25 유지
        mockMvc.perform(post("/api/v1/demo/reset").with(authentication(auth(userId)))).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM coin_ledger WHERE user_id = ? AND reason = 'DEMO_GRANT'", userId))
                .isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM video_posts")).isEqualTo(25L);
    }

    @Test
    @DisplayName("데모 계정이 아니면 리셋 진입 자체가 403(DEMO_FORBIDDEN)")
    void givenNonDemoUser_whenReset_thenForbidden() throws Exception {
        Long realUserId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null, false)))
                .toDomain().id();

        mockMvc.perform(post("/api/v1/demo/reset").with(authentication(auth(realUserId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DEMO_FORBIDDEN"));
    }
}
