package org.son.haruharu.domain.room;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomStatus;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomTodayApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired RoomJpaRepository roomJpaRepository;
    @Autowired RoomMemberJpaRepository roomMemberJpaRepository;
    @Autowired Clock clock;

    @BeforeEach
    void clean() {
        roomMemberJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("today 조회 응답에 timer_target_at(ISO 절대시각)·timer_type(NEXT_SLOT)이 snake_case로 직렬화된다")
    void givenActiveRoom_whenGetToday_thenSerializesTimerFields() throws Exception {
        Long userId = 900L;
        // starts_at 2시간 전 → 진행 중(ACTIVE), 최종선택은 이틀 뒤 → 다음 슬롯 시작 타이머(NEXT_SLOT)
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime startsAt = now.minusHours(2);
        Room room = new Room(null, RoomStatus.ACTIVE, startsAt.toLocalDate(), 3, startsAt, null,
                startsAt, now.plusDays(2), now.plusDays(2).plusHours(1),
                now.plusDays(2).plusHours(2), now.plusDays(3));
        Long roomId = roomJpaRepository.save(RoomEntity.from(room)).toDomain().id();
        roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, userId, "MALE", LocalDate.of(2000, 1, 1), startsAt)));

        mockMvc.perform(get("/api/v1/rooms/{roomId}/today", roomId).with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.current_day").value(1))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.timer_type").value("NEXT_SLOT"))
                .andExpect(jsonPath("$.data.timer_target_at").exists())
                .andExpect(jsonPath("$.data.slots.length()").value(1))
                .andExpect(jsonPath("$.data.slots[0].slot_start").exists())
                .andExpect(jsonPath("$.data.slots[0].open_now").value(true));
    }
}
