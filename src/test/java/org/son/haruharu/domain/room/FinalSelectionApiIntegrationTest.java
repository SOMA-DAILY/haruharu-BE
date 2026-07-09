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
import org.son.haruharu.domain.chat.storage.ChatRoomMemberEntity;
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
    @DisplayName("기권(selected_member_id=null) 요청 시 200·matched=false로 응답하고 null 대상 최종선택이 저장된다")
    void givenDay3Abstain_whenFinalSelection_thenSavedNullAndNotMatched() throws Exception {
        Long userId = 900L;
        // startedAt 2026-06-24 09:00 → 고정시각(2026-06-26 19:00)에 currentDay=3, 18:00 이후
        LocalDate started = LocalDate.of(2026, 6, 24);
        Room room = new Room(null, RoomStatus.ACTIVE, started, 3, started.atTime(9, 0), null);
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
                .andExpect(jsonPath("$.data.matched").value(false));

        List<FinalSelectionEntity> saved = finalSelectionJpaRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).toDomain().roomMemberId()).isEqualTo(memberId);
        assertThat(saved.get(0).toDomain().selectedMemberId()).isNull();
    }

    @Test
    @DisplayName("상호매칭 성사 시 채팅방·채팅멤버·알림함이 실제로 생성된다(AFTER_COMMIT 커밋 이후 유실되지 않는다)")
    void givenMutualFinalSelection_whenCommitted_thenChatRoomAndInboxPersisted() throws Exception {
        Long userA = 901L;
        Long userB = 902L;
        LocalDate started = LocalDate.of(2026, 6, 24);
        Room room = new Room(null, RoomStatus.ACTIVE, started, 3, started.atTime(9, 0), null);
        Long roomId = roomJpaRepository.save(RoomEntity.from(room)).toDomain().id();
        Long memberA = roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, userA, "MALE", LocalDate.of(2000, 1, 1), started.atTime(9, 0)))).toDomain().id();
        Long memberB = roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, userB, "FEMALE", LocalDate.of(2000, 1, 1), started.atTime(9, 0)))).toDomain().id();

        // A → B (아직 상호 아님)
        mockMvc.perform(post("/api/v1/rooms/{roomId}/final-selection", roomId)
                        .with(authentication(auth(userA)))
                        .contentType(APPLICATION_JSON)
                        .content("{\"selected_member_id\": " + memberB + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(false));

        // B → A (상호매칭 성사 → MutualMatchEvent → AFTER_COMMIT 리스너가 채팅방·알림함 생성)
        mockMvc.perform(post("/api/v1/rooms/{roomId}/final-selection", roomId)
                        .with(authentication(auth(userB)))
                        .contentType(APPLICATION_JSON)
                        .content("{\"selected_member_id\": " + memberA + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(true));

        // 채팅방 1개 + 매칭 참조
        assertThat(chatRoomJpaRepository.findAll()).hasSize(1);
        Long chatRoomId = chatRoomJpaRepository.findAll().get(0).toDomain().id();
        assertThat(chatRoomJpaRepository.findAll().get(0).toDomain().matchResultId()).isNotNull();

        // 두 유저가 채팅 멤버로 등록됨
        List<Long> memberUserIds = chatRoomMemberJpaRepository.findByChatRoomId(chatRoomId).stream()
                .map(ChatRoomMemberEntity::toDomain)
                .map(m -> m.userId())
                .toList();
        assertThat(memberUserIds).containsExactlyInAnyOrder(userA, userB);

        // 인앱 알림함에도 기록됨(버그 시 0행)
        assertThat(notificationHistoryJpaRepository.findAll()).isNotEmpty();
    }
}
