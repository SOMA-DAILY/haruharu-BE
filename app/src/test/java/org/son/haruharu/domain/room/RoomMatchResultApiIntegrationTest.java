package org.son.haruharu.domain.room;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.storage.ChatRoomEntity;
import org.son.haruharu.domain.chat.storage.ChatRoomJpaRepository;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserImageEntity;
import org.son.haruharu.domain.profile.storage.UserImageJpaRepository;
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
class RoomMatchResultApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired RoomJpaRepository roomJpaRepository;
    @Autowired RoomMemberJpaRepository roomMemberJpaRepository;
    @Autowired MatchResultJpaRepository matchResultJpaRepository;
    @Autowired ChatRoomJpaRepository chatRoomJpaRepository;
    @Autowired UserProfileJpaRepository userProfileJpaRepository;
    @Autowired UserImageJpaRepository userImageJpaRepository;

    @BeforeEach
    void clean() {
        chatRoomJpaRepository.deleteAll();
        matchResultJpaRepository.deleteAll();
        roomMemberJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
        userImageJpaRepository.deleteAll();
        userProfileJpaRepository.deleteAll();
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("매칭 결과 조회 시 chat_room_id·상대 프로필 요약(닉네임·대표사진)이 snake_case로 담겨 채팅방 직행이 가능하다")
    void givenMatched_whenGetMatchResult_thenReturnsChatRoomIdAndPartnerSummary() throws Exception {
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

        userProfileJpaRepository.save(UserProfileEntity.from(new UserProfile(
                null, partnerUser, "서울 마포", "디자이너", 168, "INFP", "이나경",
                DrinkingStatus.SOMETIMES, SmokingStatus.NONE)));
        // 대표사진 = sorted_order 최소. 순서를 뒤섞어 저장해도 0번이 대표로 선택돼야 한다.
        userImageJpaRepository.save(UserImageEntity.from(new UserImage(null, partnerUser, "https://cdn/second.jpg", 1)));
        userImageJpaRepository.save(UserImageEntity.from(new UserImage(null, partnerUser, "https://cdn/rep.jpg", 0)));

        Long matchResultId = matchResultJpaRepository.save(MatchResultEntity.from(
                MatchResult.of(meMemberId, partnerMemberId, LocalDateTime.of(2026, 6, 26, 22, 0)))).toDomain().id();
        Long chatRoomId = chatRoomJpaRepository.save(ChatRoomEntity.from(ChatRoom.create(matchResultId))).toDomain().id();

        mockMvc.perform(get("/api/v1/rooms/{roomId}/match-result", roomId)
                        .with(authentication(auth(meUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.partner_member_id").value(partnerMemberId.intValue()))
                .andExpect(jsonPath("$.data.chat_room_id").value(chatRoomId.intValue()))
                .andExpect(jsonPath("$.data.partner.nickname").value("이나경"))
                .andExpect(jsonPath("$.data.partner.profile_image_url").value("https://cdn/rep.jpg"));
    }
}
