package org.son.haruharu.domain.notification.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.notification.business.PushBundleService.ChatDecision;
import org.son.haruharu.infrastructure.fcm.PushStyle;

import java.util.List;

import org.son.haruharu.config.ChatFeatureProperties;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageSentEventListenerTest {

    @Mock PushSendService pushSendService;
    @Mock BlockReader blockReader;
    @Mock ChatDeliveryService chatDeliveryService;
    @Mock PushBundleService pushBundleService;

    static final Map<String, String> DIRECT_DATA = Map.of(
            "chatRoomId", "10", "message_id", "20", "sender_user_id", "1");
    static final Map<String, String> DATA = Map.of(
            "chatRoomId", "10", "message_id", "20", "sender_user_id", "1", "room_id", "5");
    static final PushStyle ALERT = PushStyle.alert("chat:10");
    static final PushStyle QUIET = PushStyle.quiet("chat:10");

    ChatMessageSentEventListener listener;

    @BeforeEach
    void setUp() {
        listener = listener(new ChatFeatureProperties(null, null));
    }

    private ChatMessageSentEventListener listener(ChatFeatureProperties properties) {
        return new ChatMessageSentEventListener(pushSendService, blockReader, chatDeliveryService, properties,
                pushBundleService);
    }

    @Test
    @DisplayName("채팅 메시지 이벤트 수신 시 수신자에게 CHAT 푸시를 chatRoomId payload·발신자 닉네임과 함께 발송한다")
    void givenEvent_whenHandle_thenSendsChatPushToRecipient() {
        allowDelivery();
        listener.handle(new ChatMessageSentEvent(
                20L, 10L, ChatRoomType.DIRECT, null, 1L,
                List.of(2L), "지예", LocalDateTime.of(2026, 9, 1, 15, 0)));

        then(pushSendService).should().sendToUsers(
                List.of(2L), NotificationType.CHAT, DIRECT_DATA, "지예");
    }

    @Test
    @DisplayName("발신자를 차단한 수신자는 CHAT 푸시 대상에서 제외된다(#408)")
    void givenBlockerRecipient_whenHandle_thenExcludedFromPush() {
        allowDelivery();
        given(blockReader.getBlockerUserIds(1L)).willReturn(Set.of(2L));
        given(pushBundleService.decideGroupChat(10L, 20L, List.of(3L))).willReturn(List.of(new ChatDecision(3L, true, 1)));

        listener.handle(new ChatMessageSentEvent(
                20L, 10L, ChatRoomType.GROUP, 5L, 1L,
                List.of(2L, 3L), "지예", LocalDateTime.of(2026, 9, 9, 15, 0)));

        then(pushSendService).should().saveInbox(List.of(3L), NotificationType.CHAT, DATA, "지예");
        then(pushSendService).should().push(List.of(3L), NotificationType.CHAT, DATA,
                "💬 새 메시지", "지예님이 메시지를 보냈어요", ALERT);
    }

    @Test
    @DisplayName("수신자 전원이 발신자를 차단했으면 푸시를 발송하지 않는다(#408)")
    void givenAllRecipientsBlocked_whenHandle_thenNoPush() {
        allowDelivery();
        given(blockReader.getBlockerUserIds(1L)).willReturn(Set.of(2L, 3L));

        listener.handle(new ChatMessageSentEvent(
                20L, 10L, ChatRoomType.GROUP, 5L, 1L,
                List.of(2L, 3L), "지예", LocalDateTime.of(2026, 9, 9, 15, 0)));

        then(pushSendService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("방이 종료되었으면 커밋 이후 대기 중인 푸시도 발송하지 않는다")
    void givenClosedRoom_whenHandle_thenDoesNotSend() {
        listener.handle(new ChatMessageSentEvent(
                20L, 10L, ChatRoomType.DIRECT, null, 1L,
                List.of(2L), "테스트", LocalDateTime.now()));
        then(pushSendService).shouldHaveNoInteractions();
        then(blockReader).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("수신자가 없으면 방 잠금과 푸시 발송을 생략한다")
    void givenNoRecipients_whenHandle_thenSkips() {
        listener.handle(new ChatMessageSentEvent(
                20L, 10L, ChatRoomType.DIRECT, null, 1L,
                List.of(), "테스트", LocalDateTime.now()));
        then(chatDeliveryService).shouldHaveNoInteractions();
        then(pushSendService).shouldHaveNoInteractions();
        then(blockReader).shouldHaveNoInteractions();
    }

    private static ChatFeatureProperties replyPush(boolean enabled) {
        return new ChatFeatureProperties(null, new ChatFeatureProperties.ReplyPush(enabled));
    }

    private static ChatMessageSentEvent replyEvent(Long replyToSender) {
        return new ChatMessageSentEvent(20L, 10L, ChatRoomType.GROUP, 5L, 1L,
                List.of(2L, 3L, 4L), "윤슬", LocalDateTime.of(2026, 9, 29, 15, 0), replyToSender);
    }

    @Test
    @DisplayName("답장 푸시 ON: 원문 주인은 CHAT_REPLY 1건만, 나머지는 CHAT (같은 사람에게 두 번 안 감, #456)")
    void givenReplyPushOn_whenReply_thenOwnerGetsReplyOthersGetChat() {
        listener = listener(replyPush(true));
        allowDelivery();
        stubAllAlert(List.of(2L, 4L));

        listener.handle(replyEvent(3L));

        then(pushSendService).should().sendToUsers(
                List.of(3L), NotificationType.CHAT_REPLY, DATA, "윤슬");
        thenGroupChatSentTo(List.of(2L, 4L));
        then(pushSendService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("답장 푸시 OFF: 원문 주인 포함 전부 기존 CHAT (#456)")
    void givenReplyPushOff_whenReply_thenAllGetChat() {
        listener = listener(replyPush(false));
        allowDelivery();
        stubAllAlert(List.of(2L, 3L, 4L));

        listener.handle(replyEvent(3L));

        thenGroupChatSentTo(List.of(2L, 3L, 4L));
        then(pushSendService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("원문 주인이 수신자가 아니면(운영 글 시스템 유저, 본인, 차단) 답장 푸시 없이 CHAT 만 (#456)")
    void givenReplyToNonRecipient_whenReply_thenNoReplyPush() {
        listener = listener(replyPush(true));
        allowDelivery();
        stubAllAlert(List.of(2L, 3L, 4L));

        listener.handle(replyEvent(999L));

        thenGroupChatSentTo(List.of(2L, 3L, 4L));
        then(pushSendService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("단체 채팅: 울릴 사람은 보통으로, 묶일 사람은 같은 칸에 안 읽은 수를 담아 조용히 보낸다(#505)")
    void givenMixedDecisions_whenGroupChat_thenAlertsAndQuietReplacesByRoom() {
        allowDelivery();
        given(pushBundleService.decideGroupChat(10L, 20L, List.of(2L, 3L, 4L))).willReturn(List.of(
                new ChatDecision(2L, true, 1), new ChatDecision(3L, false, 7), new ChatDecision(4L, false, 7)));

        listener.handle(new ChatMessageSentEvent(20L, 10L, ChatRoomType.GROUP, 5L, 1L,
                List.of(2L, 3L, 4L), "윤슬", LocalDateTime.of(2026, 10, 14, 20, 0)));

        then(pushSendService).should().saveInbox(List.of(2L, 3L, 4L), NotificationType.CHAT, DATA, "윤슬");
        then(pushSendService).should().push(List.of(2L), NotificationType.CHAT, DATA,
                "💬 새 메시지", "윤슬님이 메시지를 보냈어요", ALERT);
        then(pushSendService).should().push(List.of(3L, 4L), NotificationType.CHAT, DATA,
                "💬 새 메시지 7개", "윤슬님이 메시지를 보냈어요", QUIET);
        then(pushSendService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("단체 채팅 묶음 판단이 실패하면 모두에게 보통 알림으로 보낸다(#505)")
    void givenBundleFailure_whenGroupChat_thenAlertsEveryone() {
        allowDelivery();
        given(pushBundleService.decideGroupChat(10L, 20L, List.of(2L, 3L))).willThrow(new IllegalStateException("db"));

        listener.handle(new ChatMessageSentEvent(20L, 10L, ChatRoomType.GROUP, 5L, 1L,
                List.of(2L, 3L), "윤슬", LocalDateTime.of(2026, 10, 14, 20, 0)));

        then(pushSendService).should().saveInbox(List.of(2L, 3L), NotificationType.CHAT, DATA, "윤슬");
        then(pushSendService).should().push(List.of(2L, 3L), NotificationType.CHAT, DATA,
                "💬 새 메시지", "윤슬님이 메시지를 보냈어요", ALERT);
    }

    @Test
    @DisplayName("1:1 채팅은 묶지 않고 지금처럼 보낸다(#505)")
    void givenDirectChat_whenHandle_thenNoBundling() {
        allowDelivery();

        listener.handle(new ChatMessageSentEvent(20L, 10L, ChatRoomType.DIRECT, null, 1L,
                List.of(2L), "윤슬", LocalDateTime.of(2026, 10, 14, 20, 0)));

        then(pushSendService).should().sendToUsers(List.of(2L), NotificationType.CHAT, DIRECT_DATA, "윤슬");
        then(pushBundleService).shouldHaveNoInteractions();
    }

    private void stubAllAlert(List<Long> userIds) {
        given(pushBundleService.decideGroupChat(10L, 20L, userIds))
                .willReturn(userIds.stream().map(id -> new ChatDecision(id, true, 1)).toList());
    }

    private void thenGroupChatSentTo(List<Long> userIds) {
        then(pushSendService).should().saveInbox(userIds, NotificationType.CHAT, DATA, "윤슬");
        then(pushSendService).should().push(userIds, NotificationType.CHAT, DATA,
                "💬 새 메시지", "윤슬님이 메시지를 보냈어요", ALERT);
    }

    @Test
    @DisplayName("대화 시간 1:1(PAIR) 메시지는 상대에게 PAIR_CHAT 하나만 보내고 원본 방 id 를 싣는다. 답장이어도 CHAT_REPLY 를 따로 보내지 않는다(#540)")
    void givenPairChatReply_whenHandle_thenSinglePairChatPushWithRoomId() {
        allowDelivery();
        listener = listener(replyPush(true));

        listener.handle(new ChatMessageSentEvent(
                20L, 10L, ChatRoomType.PAIR, 104L, 1L,
                List.of(2L), "서연", LocalDateTime.of(2026, 10, 28, 20, 10), 2L));

        then(pushSendService).should().sendToUsers(List.of(2L), NotificationType.PAIR_CHAT,
                Map.of("chatRoomId", "10", "roomId", "104"), "서연");
        then(pushSendService).shouldHaveNoMoreInteractions();
        then(pushBundleService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("퇴장한 발신자의 대기 중인 채팅 알림은 보내지 않는다")
    void givenRemovedSender_whenHandle_thenSkipsPush() {
        allowDelivery();
        given(chatDeliveryService.activeUserIds(10L)).willReturn(Set.of(2L, 3L));

        listener.handle(new ChatMessageSentEvent(20L, 10L, ChatRoomType.GROUP, 5L, 1L,
                List.of(2L, 3L), "퇴장자", LocalDateTime.now()));

        then(blockReader).shouldHaveNoInteractions();
        then(pushSendService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("퇴장한 수신자에게는 채팅 알림을 보내지 않는다")
    void givenRemovedRecipient_whenHandle_thenOnlyActiveRecipientGetsPush() {
        allowDelivery();
        given(chatDeliveryService.activeUserIds(10L)).willReturn(Set.of(1L, 3L));
        stubAllAlert(List.of(3L));

        listener.handle(new ChatMessageSentEvent(20L, 10L, ChatRoomType.GROUP, 5L, 1L,
                List.of(2L, 3L), "윤슬", LocalDateTime.now()));

        thenGroupChatSentTo(List.of(3L));
        then(pushSendService).shouldHaveNoMoreInteractions();
    }

    private void allowDelivery() {
        given(chatDeliveryService.activeUserIds(10L)).willReturn(Set.of(1L, 2L, 3L, 4L));
        willAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).given(chatDeliveryService).deliverIfActive(eq(10L), any(Runnable.class));
    }
}
