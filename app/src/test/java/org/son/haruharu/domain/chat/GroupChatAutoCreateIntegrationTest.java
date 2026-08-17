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
import org.son.haruharu.domain.room.business.RoomStatusReconciler;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
