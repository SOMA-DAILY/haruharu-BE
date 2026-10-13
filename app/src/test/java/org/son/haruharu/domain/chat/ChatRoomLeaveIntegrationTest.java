package org.son.haruharu.domain.chat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.storage.ChatRoomEntity;
import org.son.haruharu.domain.chat.storage.ChatRoomJpaRepository;
import org.son.haruharu.domain.chat.storage.ChatRoomMemberEntity;
import org.son.haruharu.domain.chat.storage.ChatRoomMemberJpaRepository;
import org.son.haruharu.domain.chat.storage.exception.ChatRoomNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatRoomLeaveIntegrationTest {
    private static final AtomicLong MATCH_IDS = new AtomicLong(900_000);
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    org.springframework.messaging.simp.SimpMessagingTemplate messaging;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @Autowired ChatService chatService;
    @Autowired org.son.haruharu.domain.block.business.BlockService blockService;
    @Autowired ChatRoomJpaRepository rooms;
    @Autowired ChatRoomMemberJpaRepository members;
    @Autowired UserJpaRepository users;
    @Autowired JdbcTemplate jdbc;
    Long a;
    Long b;
    Long roomId;
    Long matchId;

    @BeforeEach
    void setUp() {
        a = users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        b = users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        matchId = MATCH_IDS.incrementAndGet();
        roomId = rooms.save(ChatRoomEntity.from(ChatRoom.createDirect(matchId))).toDomain().id();
        join(roomId, a);
        join(roomId, b);
    }

    void join(Long room, Long user) {
        members.save(ChatRoomMemberEntity.from(ChatRoomMember.join(room, user, LocalDateTime.now())));
    }

    private static UsernamePasswordAuthenticationToken auth(Long id) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of());
    }

    @Test
    @DisplayName("한 명이 나가면 DIRECT 방은 소프트 삭제되고 양쪽 목록과 접근에서 제외된다")
    void givenDirectRoom_whenOneLeaves_thenBothLoseRoom() throws Exception {
        mvc.perform(delete("/api/v1/chat-rooms/{id}/members/me", roomId).with(authentication(auth(a))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isSuccess").value(true));
        assertThat(rooms.findById(roomId)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from chat_rooms where id = ? and deleted_at is not null",
                Long.class, roomId)).isEqualTo(1);
        assertThat(members.findByChatRoomId(roomId)).allMatch(member -> member.toDomain().hasLeft());
        for (Long user : List.of(a, b)) {
            mvc.perform(get("/api/v1/chat-rooms").with(authentication(auth(user))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.chat_rooms").isEmpty());
            for (String suffix : List.of("", "/messages", "/partner")) {
                mvc.perform(get("/api/v1/chat-rooms/" + roomId + suffix).with(authentication(auth(user))))
                        .andExpect(status().isNotFound());
            }
            mvc.perform(patch("/api/v1/chat-rooms/{id}/read", roomId).with(authentication(auth(user)))
                            .contentType("application/json").content("{\"last_read_message_id\":1}"))
                    .andExpect(status().isNotFound());
            assertThatThrownBy(() -> chatService.sendMessage(roomId, user, "after close"))
                    .isInstanceOf(ChatRoomNotFoundException.class);
        }
    }

    @Test
    @DisplayName("종료 요청 재시도와 양쪽 동시 나가기의 후속 요청은 성공하며 비참여자는 거절한다")
    void givenClosedRoom_whenRetry_thenOnlyFormerMembersSucceed() throws Exception {
        chatService.leaveRoom(roomId, a);
        for (Long user : List.of(a, b, a)) {
            mvc.perform(delete("/api/v1/chat-rooms/{id}/members/me", roomId).with(authentication(auth(user))))
                    .andExpect(status().isOk());
        }
        Long outsider = users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        mvc.perform(delete("/api/v1/chat-rooms/{id}/members/me", roomId).with(authentication(auth(outsider))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("삭제된 매칭의 생성 이벤트를 재처리해도 방이 복원되거나 유니크 오류가 나지 않는다")
    void givenClosedMatch_whenCreateAgain_thenRemainsClosed() {
        chatService.leaveRoom(roomId, a);
        chatService.createRoomFromMatch(matchId, a, b);
        assertThat(rooms.findByMatchResultId(matchId)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from chat_rooms where match_result_id = ?",
                Long.class, matchId)).isEqualTo(1);
    }

    @Test
    @DisplayName("GROUP 나가기는 요청자만 퇴장시키고 방과 다른 참여자는 유지한다")
    void givenGroupRoom_whenLeave_thenOtherMemberRemains() {
        Long group = rooms.save(ChatRoomEntity.from(ChatRoom.createGroup(MATCH_IDS.incrementAndGet()))).toDomain().id();
        join(group, a);
        join(group, b);
        chatService.leaveRoom(group, a);
        assertThat(rooms.findById(group)).isPresent();
        assertThat(members.findByChatRoomIdAndUserId(group, a).orElseThrow().toDomain().hasLeft()).isTrue();
        assertThat(members.findByChatRoomIdAndUserId(group, b).orElseThrow().toDomain().hasLeft()).isFalse();
        assertThat(rooms.findById(roomId)).isPresent();
    }

    @Test
    @DisplayName("방 종료 커밋 후 양쪽 개인 큐와 방 토픽에 종료 이벤트를 한 번 전달한다")
    void givenRoom_whenLeaveCommits_thenNotifiesBothUsersOnce() {
        chatService.leaveRoom(roomId, a);
        chatService.leaveRoom(roomId, b);
        var payload = new org.son.haruharu.domain.chat.application.ChatLifecycleEventListener.RoomClosedPayload("CHAT_ROOM_CLOSED", roomId);
        org.mockito.Mockito.verify(messaging).convertAndSend(org.mockito.ArgumentMatchers.eq("/sub/chat-rooms/" + roomId),
                org.mockito.ArgumentMatchers.eq(payload), org.mockito.ArgumentMatchers.any(java.util.Map.class));
        org.mockito.Mockito.verify(messaging).convertAndSendToUser(a.toString(), "/queue/chat-room-events", payload);
        org.mockito.Mockito.verify(messaging).convertAndSendToUser(b.toString(), "/queue/chat-room-events", payload);
    }

    @Test
    @DisplayName("나가기 트랜잭션이 롤백되면 방과 참여 상태를 유지하고 종료 이벤트를 전달하지 않는다")
    void givenLeaveRollback_whenTransactionEnds_thenNoClosureBroadcast() {
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            chatService.leaveRoom(roomId, a);
            status.setRollbackOnly();
        });
        assertThat(rooms.findById(roomId)).isPresent();
        assertThat(members.findByChatRoomId(roomId)).noneMatch(m -> m.toDomain().hasLeft());
        org.mockito.Mockito.verify(messaging, org.mockito.Mockito.never()).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    @DisplayName("차단 관계에서도 나갈 수 있고 차단을 해제해도 종료한 방은 복원되지 않는다")
    void givenBlockedPartner_whenLeaveAndUnblock_thenRoomRemainsClosed() throws Exception {
        blockService.block(a, new org.son.haruharu.domain.block.business.command.CreateBlockCommand(b));
        mvc.perform(get("/api/v1/chat-rooms/{id}", roomId).with(authentication(auth(b))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/chat-rooms/{id}/members/me", roomId).with(authentication(auth(b))))
                .andExpect(status().isOk());
        blockService.unblock(a, b);
        assertThat(rooms.findById(roomId)).isEmpty();
        assertThat(members.findByChatRoomId(roomId)).allMatch(member -> member.toDomain().hasLeft());
        chatService.createRoomFromMatch(matchId, a, b);
        assertThat(rooms.findByMatchResultId(matchId)).isEmpty();
    }
}
