package org.son.haruharu.domain.room;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.storage.ChatRoomJpaRepository;
import org.son.haruharu.domain.chat.storage.ChatRoomMemberJpaRepository;
import org.son.haruharu.domain.notification.storage.NotificationHistoryJpaRepository;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.storage.FinalSelectionEntity;
import org.son.haruharu.domain.room.storage.FinalSelectionJpaRepository;
import org.son.haruharu.domain.room.storage.MatchResultJpaRepository;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class FinalSelectionApiIntegrationTest {

    /** Day3(2026-06-26) 19:00 KST 고정 → 최종선택 오픈(18:00) 이후로 게이트를 통과시킨다. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-06-26T10:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired RoomJpaRepository roomJpaRepository;
    @Autowired RoomMemberJpaRepository roomMemberJpaRepository;
    @Autowired FinalSelectionJpaRepository finalSelectionJpaRepository;
    @Autowired MatchResultJpaRepository matchResultJpaRepository;
    @Autowired ChatRoomJpaRepository chatRoomJpaRepository;
    @Autowired ChatRoomMemberJpaRepository chatRoomMemberJpaRepository;
    @Autowired NotificationHistoryJpaRepository notificationHistoryJpaRepository;

    @BeforeEach
    void clean() {
        chatRoomMemberJpaRepository.deleteAll();
        chatRoomJpaRepository.deleteAll();
        matchResultJpaRepository.deleteAll();
        notificationHistoryJpaRepository.deleteAll();
        finalSelectionJpaRepository.deleteAll();
        roomMemberJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("기권(selected_member_id=null) 요청 시 200·submitted=true로 응답하고 null 대상 최종선택이 저장된다")
    void givenDay3Abstain_whenFinalSelection_thenSavedNullAndSubmitted() throws Exception {
        Long userId = 900L;
        // startedAt 2026-06-24 09:00 → 고정시각(2026-06-26 19:00)에 currentDay=3, 18:00 이후
        LocalDate started = LocalDate.of(2026, 6, 24);
        Room room = new Room(null, RoomStatus.ACTIVE, started, 3, started.atTime(9, 0), null,
                started.atTime(9, 0), started.plusDays(2).atTime(18, 0), started.plusDays(2).atTime(22, 0),
                started.plusDays(2).atTime(22, 0), started.plusDays(3).atTime(9, 0));
        Long roomId = roomJpaRepository.save(RoomEntity.from(room)).toDomain().id();
        Long memberId = roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, userId, "MALE", LocalDate.of(2000, 1, 1), started.atTime(9, 0)))).toDomain().id();

        mockMvc.perform(post("/api/v1/rooms/{roomId}/final-selection", roomId)
                        .with(authentication(auth(userId)))
                        .contentType(APPLICATION_JSON)
                        .content("{\"selected_member_id\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.selected_member_id").value(nullValue()))
                .andExpect(jsonPath("$.data.submitted").value(true));

        List<FinalSelectionEntity> saved = finalSelectionJpaRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).toDomain().roomMemberId()).isEqualTo(memberId);
        assertThat(saved.get(0).toDomain().selectedMemberId()).isNull();
    }

    @Test
    @DisplayName("동시공개(#343): 상대를 선택해 제출하면 select 시점엔 매칭·채팅방 생성 없이 submitted=true만 응답한다")
    void givenMutualSubmit_whenSelect_thenSubmittedWithoutImmediateMatch() throws Exception {
        Long userA = 901L;
        Long userB = 902L;
        LocalDate started = LocalDate.of(2026, 6, 24);
        Room room = new Room(null, RoomStatus.ACTIVE, started, 3, started.atTime(9, 0), null,
                started.atTime(9, 0), started.plusDays(2).atTime(18, 0), started.plusDays(2).atTime(22, 0),
                started.plusDays(2).atTime(22, 0), started.plusDays(3).atTime(9, 0));
        Long roomId = roomJpaRepository.save(RoomEntity.from(room)).toDomain().id();
        Long memberA = roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, userA, "MALE", LocalDate.of(2000, 1, 1), started.atTime(9, 0)))).toDomain().id();
        Long memberB = roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, userB, "FEMALE", LocalDate.of(2000, 1, 1), started.atTime(9, 0)))).toDomain().id();

        // A → B
        mockMvc.perform(post("/api/v1/rooms/{roomId}/final-selection", roomId)
                        .with(authentication(auth(userA)))
                        .contentType(APPLICATION_JSON)
                        .content("{\"selected_member_id\": " + memberB + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submitted").value(true));

        // B → A — 상호 선택이지만 동시공개라 select 시점엔 매칭 판정을 하지 않는다(result_at 도달 시 Resolver가 처리).
        mockMvc.perform(post("/api/v1/rooms/{roomId}/final-selection", roomId)
                        .with(authentication(auth(userB)))
                        .contentType(APPLICATION_JSON)
                        .content("{\"selected_member_id\": " + memberA + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submitted").value(true));

        // 선택은 저장되지만 select 시점엔 매칭 결과·채팅방이 아직 생성되지 않는다.
        assertThat(finalSelectionJpaRepository.findAll()).hasSize(2);
        assertThat(matchResultJpaRepository.findAll()).isEmpty();
        assertThat(chatRoomJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("최종선택 미제출 상태에서 조회하면 404가 아닌 200·submitted=false로 응답한다(상태 복원용)")
    void givenNotSubmitted_whenGetFinalSelection_thenSubmittedFalse() throws Exception {
        Long userId = 903L;
        LocalDate started = LocalDate.of(2026, 6, 24);
        Room room = new Room(null, RoomStatus.ACTIVE, started, 3, started.atTime(9, 0), null,
                started.atTime(9, 0), started.plusDays(2).atTime(18, 0), started.plusDays(2).atTime(22, 0),
                started.plusDays(2).atTime(22, 0), started.plusDays(3).atTime(9, 0));
        Long roomId = roomJpaRepository.save(RoomEntity.from(room)).toDomain().id();
        roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, userId, "MALE", LocalDate.of(2000, 1, 1), started.atTime(9, 0))));

        mockMvc.perform(get("/api/v1/rooms/{roomId}/final-selection", roomId)
                        .with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.submitted").value(false))
                .andExpect(jsonPath("$.data.selected_member_id").value(nullValue()))
                .andExpect(jsonPath("$.data.selected_at").value(nullValue()));
    }

    @Test
    @DisplayName("최종선택 제출 후 조회하면 submitted=true와 선택 대상·제출 시각이 복원된다")
    void givenSubmitted_whenGetFinalSelection_thenReturnsSelection() throws Exception {
        Long userA = 904L;
        Long userB = 905L;
        LocalDate started = LocalDate.of(2026, 6, 24);
        Room room = new Room(null, RoomStatus.ACTIVE, started, 3, started.atTime(9, 0), null,
                started.atTime(9, 0), started.plusDays(2).atTime(18, 0), started.plusDays(2).atTime(22, 0),
                started.plusDays(2).atTime(22, 0), started.plusDays(3).atTime(9, 0));
        Long roomId = roomJpaRepository.save(RoomEntity.from(room)).toDomain().id();
        roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, userA, "MALE", LocalDate.of(2000, 1, 1), started.atTime(9, 0))));
        Long memberB = roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, userB, "FEMALE", LocalDate.of(2000, 1, 1), started.atTime(9, 0)))).toDomain().id();

        mockMvc.perform(post("/api/v1/rooms/{roomId}/final-selection", roomId)
                        .with(authentication(auth(userA)))
                        .contentType(APPLICATION_JSON)
                        .content("{\"selected_member_id\": " + memberB + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/rooms/{roomId}/final-selection", roomId)
                        .with(authentication(auth(userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submitted").value(true))
                .andExpect(jsonPath("$.data.selected_member_id").value(memberB.intValue()))
                .andExpect(jsonPath("$.data.selected_at").isNotEmpty());
    }
}
