package org.son.haruharu.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.storage.RoomEntity;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoRoomApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired RoomJpaRepository roomJpaRepository;
    @Autowired RoomReader roomReader;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM room_members");
        jdbcTemplate.update("DELETE FROM rooms");
        jdbcTemplate.update("DELETE FROM users");
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private Long saveDemoUser() {
        return userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null, true)))
                .toDomain().id();
    }

    @Test
    @DisplayName("데모 계정이 방을 생성하면 Day1 데모 방이 만들어지고, advance-day로 Day가 진행된다")
    void givenDemoUser_whenCreateThenAdvanceDay_thenDayProgresses() throws Exception {
        Long userId = saveDemoUser();

        mockMvc.perform(post("/api/v1/demo/room").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.day").value(1))
                .andExpect(jsonPath("$.data.room_id").isNumber());

        // 저장된 방이 demo=true·demo_day=1 (정리된 상태라 방은 이 하나)
        Room room = roomJpaRepository.findAll().get(0).toDomain();
        assertThat(room.demo()).isTrue();
        assertThat(room.demoDay()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/demo/room/advance-day").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.day").value(2));
    }

    @Test
    @DisplayName("데모 방은 배치 진입점 getActive()에서 제외되고, 일반 ACTIVE 방은 포함된다(배치 격리)")
    void givenDemoRoom_whenGetActive_thenExcludedButNormalIncluded() throws Exception {
        Long userId = saveDemoUser();
        // 데모 방 생성
        mockMvc.perform(post("/api/v1/demo/room").with(authentication(auth(userId)))).andExpect(status().isOk());
        // 일반 ACTIVE 방 시드
        Long normalRoomId = roomJpaRepository.save(RoomEntity.from(
                Room.create(LocalDate.now(), 3).started(LocalDateTime.now()))).toDomain().id();

        List<Room> active = roomReader.getActive();

        assertThat(active).extracting(Room::id).contains(normalRoomId);
        assertThat(active).allMatch(r -> !r.demo()); // 데모 방은 하나도 없음
    }

    @Test
    @DisplayName("방 없이 advance-day 호출 시 404 DEMO_ROOM_NOT_FOUND")
    void givenNoRoom_whenAdvanceDay_thenReturns404() throws Exception {
        Long userId = saveDemoUser();

        mockMvc.perform(post("/api/v1/demo/room/advance-day").with(authentication(auth(userId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEMO_ROOM_NOT_FOUND"));
    }
}
