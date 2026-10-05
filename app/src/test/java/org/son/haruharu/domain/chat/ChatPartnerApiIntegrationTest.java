package org.son.haruharu.domain.chat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.storage.ChatRoomEntity;
import org.son.haruharu.domain.chat.storage.ChatRoomJpaRepository;
import org.son.haruharu.domain.chat.storage.ChatRoomMemberEntity;
import org.son.haruharu.domain.chat.storage.ChatRoomMemberJpaRepository;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.profile.storage.UserProfileJpaRepository;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.storage.MatchResultEntity;
import org.son.haruharu.domain.room.storage.MatchResultJpaRepository;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.storage.VideoPostEntity;
import org.son.haruharu.domain.video.storage.VideoPostJpaRepository;
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
class ChatPartnerApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired RoomJpaRepository roomJpaRepository;
    @Autowired RoomMemberJpaRepository roomMemberJpaRepository;
    @Autowired MatchResultJpaRepository matchResultJpaRepository;
    @Autowired ChatRoomJpaRepository chatRoomJpaRepository;
    @Autowired ChatRoomMemberJpaRepository chatRoomMemberJpaRepository;
    @Autowired VideoPostJpaRepository videoPostJpaRepository;
    @Autowired UserProfileJpaRepository userProfileJpaRepository;
    @Autowired UserJpaRepository userJpaRepository;

    @BeforeEach
    void clean() {
        videoPostJpaRepository.deleteAll();
        chatRoomMemberJpaRepository.deleteAll();
        chatRoomJpaRepository.deleteAll();
        matchResultJpaRepository.deleteAll();
        roomMemberJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
        userProfileJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
    }

    private Long saveUser(UserStatus status) {
        return userJpaRepository.save(UserEntity.from(new User(null, status, null))).toDomain().id();
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private Long saveVideo(Long roomMemberId, int day, int slot) {
        // slotStart는 day·slot로부터 유일하게 파생(slot_start 충돌 방지)
        LocalDateTime slotStart = LocalDateTime.of(2026, 6, 23 + day, 8 + slot, 0);
        return videoPostJpaRepository.save(VideoPostEntity.from(VideoPost.create(
                roomMemberId, slotStart, null, "cap", "https://cdn/v-" + day + "-" + slot + ".mp4",
                "https://cdn/t-" + day + "-" + slot + ".jpg", LocalDateTime.of(2026, 6, 24, 10, 0)))).toDomain().id();
    }

    @Test
    @DisplayName("매칭 상대 카드 조회 시 프로필 + videos(slot_start 오름차순 영상)가 snake_case로 직렬화된다")
    void givenMatchedPartner_whenGetPartner_thenReturnsProfileAndVideos() throws Exception {
        Long meUser = saveUser(UserStatus.ACTIVE);
        Long partnerUser = saveUser(UserStatus.ACTIVE);
        LocalDate started = LocalDate.of(2026, 6, 24);
        Long roomId = roomJpaRepository.save(RoomEntity.from(
                new Room(null, RoomStatus.ENDED, started, 3, started.atTime(9, 0), started.plusDays(3).atTime(9, 0))))
                .toDomain().id();
        Long meMemberId = roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, meUser, "MALE", LocalDate.of(2000, 1, 1), started.atTime(9, 0)))).toDomain().id();
        Long partnerMemberId = roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, partnerUser, "FEMALE", LocalDate.of(1999, 1, 1), started.atTime(9, 0)))).toDomain().id();

        // 상대 프로필(닉네임·필드값 확인용)
        userProfileJpaRepository.save(UserProfileEntity.from(new UserProfile(
                null, partnerUser, "서울 마포", "디자이너", 168, "INFP", "이나경",
                DrinkingStatus.SOMETIMES, SmokingStatus.NONE, null)));

        Long matchResultId = matchResultJpaRepository.save(MatchResultEntity.from(
                MatchResult.of(meMemberId, partnerMemberId, LocalDateTime.of(2026, 6, 26, 22, 0)))).toDomain().id();
        Long chatRoomId = chatRoomJpaRepository.save(ChatRoomEntity.from(ChatRoom.createDirect(matchResultId))).toDomain().id();
        chatRoomMemberJpaRepository.save(ChatRoomMemberEntity.from(
                ChatRoomMember.join(chatRoomId, meUser, LocalDateTime.now())));
        chatRoomMemberJpaRepository.save(ChatRoomMemberEntity.from(
                ChatRoomMember.join(chatRoomId, partnerUser, LocalDateTime.now())));

        // 상대 영상: day1(slot1,2), day2(slot1), day3(slot3)
        saveVideo(partnerMemberId, 1, 1);
        saveVideo(partnerMemberId, 1, 2);
        saveVideo(partnerMemberId, 2, 1);
        saveVideo(partnerMemberId, 3, 3);

        mockMvc.perform(get("/api/v1/chat-rooms/{chatRoomId}/partner", chatRoomId)
                        .with(authentication(auth(meUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.user_id").value(partnerUser.intValue()))
                .andExpect(jsonPath("$.data.profile.nickname").value("이나경"))
                // 프로필 블록(fields/value_answers) 구조 존재. 항목 값 공개는 매칭공개(#138)와 함께 동작.
                .andExpect(jsonPath("$.data.profile.fields").isArray())
                .andExpect(jsonPath("$.data.profile.fields[?(@.key == 'MBTI')]").exists())
                .andExpect(jsonPath("$.data.profile.fields[0].available_from_day").isNumber())
                .andExpect(jsonPath("$.data.videos", org.hamcrest.Matchers.hasSize(4)))
                .andExpect(jsonPath("$.data.videos[0].video_url").value("https://cdn/v-1-1.mp4"))
                .andExpect(jsonPath("$.data.videos[0].slot_start").value("2026-06-24T09:00:00"))
                .andExpect(jsonPath("$.data.videos[3].slot_start").value("2026-06-26T11:00:00"));
    }

    @Test
    @DisplayName("상대가 탈퇴하면 채팅방 목록은 200으로 탈퇴 표시만 내려주고, 방 진입은 403으로 막힌다 (#255)")
    void givenWithdrawnPartner_whenGetChatRooms_thenListSurvivesAndRoomIsBlocked() throws Exception {
        Long meUser = saveUser(UserStatus.ACTIVE);
        Long partnerUser = saveUser(UserStatus.DELETED); // 탈퇴 완료 상태
        LocalDate started = LocalDate.of(2026, 6, 24);
        Long roomId = roomJpaRepository.save(RoomEntity.from(
                new Room(null, RoomStatus.ENDED, started, 3, started.atTime(9, 0), started.plusDays(3).atTime(9, 0))))
                .toDomain().id();
        Long meMemberId = roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, meUser, "MALE", LocalDate.of(2000, 1, 1), started.atTime(9, 0)))).toDomain().id();
        Long partnerMemberId = roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, partnerUser, "FEMALE", LocalDate.of(1999, 1, 1), started.atTime(9, 0))))
                .toDomain().id();
        // 탈퇴 시 프로필은 soft-delete되어 조회 불가 — 그래서 user_profiles 행을 아예 만들지 않는다
        Long matchResultId = matchResultJpaRepository.save(MatchResultEntity.from(
                MatchResult.of(meMemberId, partnerMemberId, LocalDateTime.of(2026, 6, 26, 22, 0)))).toDomain().id();
        Long chatRoomId = chatRoomJpaRepository.save(ChatRoomEntity.from(ChatRoom.createDirect(matchResultId))).toDomain().id();
        chatRoomMemberJpaRepository.save(ChatRoomMemberEntity.from(
                ChatRoomMember.join(chatRoomId, meUser, LocalDateTime.now())));
        chatRoomMemberJpaRepository.save(ChatRoomMemberEntity.from(
                ChatRoomMember.join(chatRoomId, partnerUser, LocalDateTime.now())));

        // 목록은 살아있어야 한다 — 이 API가 404로 죽는 것이 #255의 증상이었다
        mockMvc.perform(get("/api/v1/chat-rooms").with(authentication(auth(meUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.chat_rooms", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.chat_rooms[0].chat_room_id").value(chatRoomId.intValue()))
                .andExpect(jsonPath("$.data.chat_rooms[0].partner_withdrawn").value(true))
                .andExpect(jsonPath("$.data.chat_rooms[0].partner").doesNotExist())
                .andExpect(jsonPath("$.data.chat_rooms[0].last_message").doesNotExist());

        // 진입·기록 열람은 막힌다
        mockMvc.perform(get("/api/v1/chat-rooms/{id}", chatRoomId).with(authentication(auth(meUser))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHAT_PARTNER_WITHDRAWN"));
        mockMvc.perform(get("/api/v1/chat-rooms/{id}/messages", chatRoomId).with(authentication(auth(meUser))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHAT_PARTNER_WITHDRAWN"));
        mockMvc.perform(get("/api/v1/chat-rooms/{id}/partner", chatRoomId).with(authentication(auth(meUser))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHAT_PARTNER_WITHDRAWN"));
    }
}
