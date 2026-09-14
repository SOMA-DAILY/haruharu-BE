package org.son.haruharu.domain.chat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageWriter;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.room.business.RoomInviteService;
import org.son.haruharu.domain.room.business.RoomStatusTransitionService;
import org.son.haruharu.domain.room.business.RoomStatusReconciler;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GROUP 채팅 자동 생성 end-to-end. PENDING→ACTIVE 전이(RoomStartedEvent 커밋 후)에서
 * GroupChatAutoCreateListener 가 방 멤버 스냅샷으로 GROUP 채팅방을 생성하는지 실제 DB로 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class GroupChatAutoCreateIntegrationTest {

    @Autowired RoomStatusReconciler reconciler;
    @Autowired RoomWriter roomWriter;
    @Autowired RoomMemberWriter roomMemberWriter;
    @Autowired ChatRoomReader chatRoomReader;
    @Autowired ChatRoomMemberReader chatRoomMemberReader;
    @Autowired RoomInviteService roomInviteService;
    @Autowired ChatService chatService;
    @Autowired ChatMessageWriter chatMessageWriter;
    @Autowired RoomStatusTransitionService transitionService;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("시작 후 초대코드로 입장한 멤버도 그룹채팅에 중복 없이 참여한다")
    void givenStartedRoom_whenJoinWithInvite_thenGroupMembershipExists() {
        Room room = persistActivatingRoom(LocalDateTime.now().minusHours(1));
        roomInviteService.issue(room.id(), "LATE46", null, null, "test");
        reconciler.reconcile(LocalDateTime.now());
        ChatRoom group = chatRoomReader.findByRoomId(room.id()).orElseThrow();
        chatMessageWriter.save(ChatMessage.create(group.id(), 9415L, "입장 전 대화"));

        roomInviteService.join(9416L, "LATE46");
        roomInviteService.join(9416L, "LATE46");

        assertThat(chatRoomMemberReader.findByChatRoomId(group.id()))
                .extracting(ChatRoomMember::userId).containsExactly(9416L);
        assertThat(chatService.getGroupRoomBySourceRoom(room.id(), 9416L)).isNotNull();
        assertThat(chatService.getMyRooms(9416L).chatRooms())
                .extracting(summary -> summary.chatRoomId()).contains(group.id());
        assertThat(chatService.getMessages(group.id(), 9416L, null, 30).messages())
                .extracting(message -> message.content()).contains("입장 전 대화");
        assertThat(chatService.sendMessage(group.id(), 9416L, "늦은 입장자의 메시지").messageId()).isNotNull();
        assertThat(chatService.getMessages(group.id(), 9416L, null, 30).messages())
                .extracting(message -> message.content()).contains("늦은 입장자의 메시지");
    }

    @Test
    @DisplayName("PENDING 방 입장은 채팅을 만들지 않고 방 시작 시 스냅샷으로 참여한다")
    void givenPendingRoom_whenJoinThenStart_thenGroupMembershipExists() {
        LocalDateTime startsAt = LocalDateTime.now().plusHours(1);
        Room room = persistActivatingRoom(startsAt);
        roomInviteService.issue(room.id(), "WAJT46", null, null, "test");
        roomInviteService.join(9417L, "WAJT46");
        assertThat(chatRoomReader.findByRoomId(room.id())).isEmpty();

        transitionService.transition(room, startsAt);

        ChatRoom group = chatRoomReader.findByRoomId(room.id()).orElseThrow();
        assertThat(chatRoomMemberReader.findByChatRoomId(group.id()))
                .extracting(ChatRoomMember::userId).containsExactly(9417L);
    }

    @Test
    @DisplayName("입장 트랜잭션이 롤백되면 채팅 멤버 등록도 롤백된다")
    void givenGroupChat_whenJoinRollsBack_thenNoChatMembership() {
        Room room = persistActivatingRoom(LocalDateTime.now().minusHours(1));
        roomInviteService.issue(room.id(), "BACK46", null, null, "test");
        transitionService.transition(room, LocalDateTime.now());
        ChatRoom group = chatRoomReader.findByRoomId(room.id()).orElseThrow();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            roomInviteService.join(9418L, "BACK46");
            assertThat(chatRoomMemberReader.findMembership(group.id(), 9418L)).isPresent();
            status.setRollbackOnly();
        });

        assertThat(chatRoomMemberReader.findMembership(group.id(), 9418L)).isEmpty();
    }

    private Room persistActivatingRoom(LocalDateTime startsAt) {
        return roomWriter.save(Room.createScheduled(
                startsAt, startsAt.plusDays(2).withHour(18), startsAt.plusDays(2).withHour(21),
                startsAt.plusDays(2).withHour(22), startsAt.plusDays(3).withHour(9), 3));
    }

    @Test
    @DisplayName("방이 ACTIVE로 전이되면 방 멤버 전원으로 GROUP 채팅방이 자동 생성된다")
    void givenRoomActivates_thenGroupChatCreatedWithAllMembers() {
        LocalDateTime startsAt = LocalDateTime.now().minusHours(1);
        Room room = persistActivatingRoom(startsAt);
        List<Long> userIds = List.of(901L, 902L, 903L, 904L, 905L, 906L);
        roomMemberWriter.saveAll(userIds.stream()
                .map(uid -> RoomMember.of(room.id(), uid, "MALE", LocalDate.of(2000, 1, 1), startsAt))
                .toList());

        reconciler.reconcile(LocalDateTime.now()); // PENDING→ACTIVE 커밋 → RoomStartedEvent → GROUP 생성

        ChatRoom groupChat = chatRoomReader.findByRoomId(room.id()).orElseThrow();
        assertThat(groupChat.type()).isEqualTo(ChatRoomType.GROUP);
        assertThat(groupChat.roomId()).isEqualTo(room.id());
        assertThat(chatRoomMemberReader.findByChatRoomId(groupChat.id()))
                .extracting(ChatRoomMember::userId)
                .containsExactlyInAnyOrderElementsOf(userIds);
    }

    @Test
    @DisplayName("이미 GROUP 채팅이 있는 방은 재전이(reconcile 재실행)에도 중복 생성되지 않는다(멱등)")
    void givenGroupChatExists_whenReconcileAgain_thenNoDuplicate() {
        LocalDateTime startsAt = LocalDateTime.now().minusHours(1);
        Room room = persistActivatingRoom(startsAt);
        roomMemberWriter.saveAll(List.of(
                RoomMember.of(room.id(), 911L, "MALE", LocalDate.of(2000, 1, 1), startsAt),
                RoomMember.of(room.id(), 912L, "FEMALE", LocalDate.of(2000, 1, 1), startsAt)));

        reconciler.reconcile(LocalDateTime.now());
        reconciler.reconcile(LocalDateTime.now()); // 두 번째는 이미 ACTIVE라 전이 이벤트 없음

        // room_id 당 GROUP 채팅 1방(멱등). 재전이에도 중복 생성되지 않는다.
        assertThat(chatRoomReader.findByRoomId(room.id())).isPresent();
    }
}
