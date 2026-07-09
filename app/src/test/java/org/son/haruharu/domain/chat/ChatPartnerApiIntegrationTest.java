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

    @BeforeEach
    void clean() {
        videoPostJpaRepository.deleteAll();
        chatRoomMemberJpaRepository.deleteAll();
        chatRoomJpaRepository.deleteAll();
        matchResultJpaRepository.deleteAll();
        roomMemberJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
        userProfileJpaRepository.deleteAll();
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private Long saveVideo(Long roomMemberId, int day, int slot) {
        return videoPostJpaRepository.save(VideoPostEntity.from(VideoPost.create(
                roomMemberId, day, slot, null, "cap", "https://cdn/v-" + day + "-" + slot + ".mp4",
                "https://cdn/t-" + day + "-" + slot + ".jpg", LocalDateTime.of(2026, 6, 24, 10, 0)))).toDomain().id();
    }

    @Test
    @DisplayName("매칭 상대 카드 조회 시 프로필 + videos_by_day(day별 영상)가 snake_case로 직렬화된다")
    void givenMatchedPartner_whenGetPartner_thenReturnsProfileAndVideosByDay() throws Exception {
        Long meUser = 900L;
        Long partnerUser = 901L;
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
                DrinkingStatus.SOMETIMES, SmokingStatus.NONE)));

        Long matchResultId = matchResultJpaRepository.save(MatchResultEntity.from(
                MatchResult.of(meMemberId, partnerMemberId, LocalDateTime.of(2026, 6, 26, 22, 0)))).toDomain().id();
        Long chatRoomId = chatRoomJpaRepository.save(ChatRoomEntity.from(ChatRoom.create(matchResultId))).toDomain().id();
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
                .andExpect(jsonPath("$.data.profile.fields[?(@.key == 'location')]").exists())
                .andExpect(jsonPath("$.data.videos_by_day['1']", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.data.videos_by_day['1'][0].video_url").value("https://cdn/v-1-1.mp4"))
                .andExpect(jsonPath("$.data.videos_by_day['1'][0].slot_index").value(1))
                .andExpect(jsonPath("$.data.videos_by_day['2']", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.videos_by_day['3'][0].slot_index").value(3));
    }
}
