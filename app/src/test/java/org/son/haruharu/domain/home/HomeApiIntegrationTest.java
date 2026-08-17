package org.son.haruharu.domain.home;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired RoomMemberJpaRepository roomMemberJpaRepository;
    @Autowired RoomJpaRepository roomJpaRepository;

    // "user 900은 어느 방에도 없다"는 전제를 스스로 보장한다(#166) — 같은 900을 쓰는
    // RoomToday/FinalSelection 통합 테스트가 실행 후 ACTIVE room을 남겨 active_room 단언이 깨질 수 있다.
    @BeforeEach
    void clean() {
        roomMemberJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("참여 방 없는 유저의 홈은 active_room·pending_result가 null로 직렬화된다(초대코드 입력 유도)")
    void givenNoRoom_whenGetHome_thenNullFields() throws Exception {
        mockMvc.perform(get("/api/v1/home").with(authentication(auth(900L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.now").exists())
                .andExpect(jsonPath("$.data.active_room").value(nullValue()))
                .andExpect(jsonPath("$.data.pending_result").value(nullValue()));
    }

    @Test
    @DisplayName("참여 중인 방이 있으면 active_room에 room_id·status·starts_at·ends_at(snake_case)이 내려간다")
    void givenActiveRoom_whenGetHome_thenActiveRoomExposed() throws Exception {
        LocalDateTime startsAt = LocalDateTime.now().minusHours(2);
        Room room = new Room(null, org.son.haruharu.domain.room.implement.RoomStatus.ACTIVE,
                startsAt.toLocalDate(), 3, startsAt, null,
                startsAt, startsAt.plusDays(2), startsAt.plusDays(2).plusHours(1),
                startsAt.plusDays(2).plusHours(2), startsAt.plusDays(3));
        Long roomId = roomJpaRepository.save(RoomEntity.from(room)).toDomain().id();
        roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, 900L, "MALE", LocalDate.of(2000, 1, 1), startsAt)));

        mockMvc.perform(get("/api/v1/home").with(authentication(auth(900L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active_room.room_id").value(roomId.intValue()))
                .andExpect(jsonPath("$.data.active_room.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.active_room.starts_at").exists())
                .andExpect(jsonPath("$.data.active_room.ends_at").exists());
    }

    @Test
    @DisplayName("인증 없이 홈 조회 시 401을 반환한다")
    void givenNoAuth_whenGetHome_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isUnauthorized());
    }
}
