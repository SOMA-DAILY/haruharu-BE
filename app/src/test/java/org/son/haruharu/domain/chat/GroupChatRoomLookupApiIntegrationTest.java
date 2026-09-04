package org.son.haruharu.domain.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.storage.ChatRoomEntity;
import org.son.haruharu.domain.chat.storage.ChatRoomJpaRepository;
import org.son.haruharu.domain.chat.storage.ChatRoomMemberEntity;
import org.son.haruharu.domain.chat.storage.ChatRoomMemberJpaRepository;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GroupChatRoomLookupApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ChatRoomMemberJpaRepository chatRoomMemberJpaRepository;
    @Autowired ChatRoomJpaRepository chatRoomJpaRepository;
    @Autowired RoomJpaRepository roomJpaRepository;
    @Autowired UserJpaRepository userJpaRepository;

    @BeforeEach
    void clean() {
        chatRoomMemberJpaRepository.deleteAll();
        chatRoomJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
    }

    private Long saveUser() {
        return userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null)))
                .toDomain().id();
    }

    private Long saveRoom() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 3, 9, 0);
        return roomJpaRepository.save(RoomEntity.from(Room.createScheduled(
                startsAt, startsAt.plusDays(2).withHour(18), startsAt.plusDays(2).withHour(21),
                startsAt.plusDays(2).withHour(22), startsAt.plusDays(3), 3)))
                .toDomain().id();
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("여러 GROUP 채팅의 활성 멤버는 요청한 방마다 정확한 채팅방 ID를 조회한다")
    void givenMultipleActiveGroupChats_whenGetEachRoom_thenReturnsEachChatRoomId() throws Exception {
        Long userId = saveUser();
        Long firstRoomId = saveRoom();
        Long secondRoomId = saveRoom();
        Long firstChatRoomId = chatRoomJpaRepository.save(
                        ChatRoomEntity.from(ChatRoom.createGroup(firstRoomId)))
                .toDomain().id();
        Long secondChatRoomId = chatRoomJpaRepository.save(
                        ChatRoomEntity.from(ChatRoom.createGroup(secondRoomId)))
                .toDomain().id();
        chatRoomMemberJpaRepository.save(ChatRoomMemberEntity.from(
                ChatRoomMember.join(firstChatRoomId, userId, LocalDateTime.now())));
        chatRoomMemberJpaRepository.save(ChatRoomMemberEntity.from(
                ChatRoomMember.join(secondChatRoomId, userId, LocalDateTime.now())));

        mockMvc.perform(get("/api/v1/chat-rooms/group/by-room/{roomId}", firstRoomId)
                        .with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.chat_room_id").value(firstChatRoomId));

        mockMvc.perform(get("/api/v1/chat-rooms/group/by-room/{roomId}", secondRoomId)
                        .with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.chat_room_id").value(secondChatRoomId));
    }

    @Test
    @DisplayName("GROUP 채팅이 생성되기 전이면 404를 반환한다")
    void givenGroupChatNotCreated_whenGetByRoom_thenReturnsNotFound() throws Exception {
        Long userId = saveUser();
        Long roomId = saveRoom();

        mockMvc.perform(get("/api/v1/chat-rooms/group/by-room/{roomId}", roomId)
                        .with(authentication(auth(userId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHAT_ROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("GROUP 채팅 비멤버 또는 퇴장 멤버는 403을 반환한다")
    void givenInactiveGroupChatMember_whenGetByRoom_thenReturnsForbidden() throws Exception {
        Long memberUserId = saveUser();
        Long outsiderUserId = saveUser();
        Long roomId = saveRoom();
        Long chatRoomId = chatRoomJpaRepository.save(ChatRoomEntity.from(ChatRoom.createGroup(roomId)))
                .toDomain().id();
        ChatRoomMember left = ChatRoomMember.join(chatRoomId, memberUserId, LocalDateTime.now())
                .leave(LocalDateTime.now());
        chatRoomMemberJpaRepository.save(ChatRoomMemberEntity.from(left));

        for (Long userId : List.of(memberUserId, outsiderUserId)) {
            mockMvc.perform(get("/api/v1/chat-rooms/group/by-room/{roomId}", roomId)
                            .with(authentication(auth(userId))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("CHAT_NOT_MEMBER"));
        }
    }

    @Test
    @DisplayName("미인증 요청은 401을 반환한다")
    void givenUnauthenticated_whenGetByRoom_thenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/chat-rooms/group/by-room/{roomId}", 1L))
                .andExpect(status().isUnauthorized());
    }
}
