package org.son.haruharu.domain.room;

import java.time.Clock;
import java.time.LocalDate;
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
    void givenActiveDay1Room_whenGetToday_thenSerializesTimerFields() throws Exception {
        Long userId = 900L;
        // 오늘 09:00 시작 앵커 → 실행 시각과 무관하게 currentDay=1(마지막 날 아님) → 타이머는 항상 NEXT_SLOT
        LocalDate today = LocalDate.now(clock);
        Room room = new Room(null, org.son.haruharu.domain.room.implement.RoomStatus.ACTIVE,
                today, 3, today.atTime(9, 0), null);
        Long roomId = roomJpaRepository.save(RoomEntity.from(room)).toDomain().id();
        roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, userId, "MALE", LocalDate.of(2000, 1, 1), today.atTime(9, 0))));

        mockMvc.perform(get("/api/v1/rooms/{roomId}/today", roomId).with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.current_day").value(1))
                .andExpect(jsonPath("$.data.total_slots").value(5))
                .andExpect(jsonPath("$.data.timer_type").value("NEXT_SLOT"))
                .andExpect(jsonPath("$.data.timer_target_at").exists());
    }
}
