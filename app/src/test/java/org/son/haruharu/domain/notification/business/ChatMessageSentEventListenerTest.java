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

    ChatMessageSentEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChatMessageSentEventListener(pushSendService, blockReader, chatDeliveryService,
                new ChatFeatureProperties(null, null, null));
    }

    @Test
    @DisplayName("채팅 메시지 이벤트 수신 시 수신자에게 CHAT 푸시를 chatRoomId payload·발신자 닉네임과 함께 발송한다")
    void givenEvent_whenHandle_thenSendsChatPushToRecipient() {
        allowDelivery();
        listener.handle(new ChatMessageSentEvent(
                20L, 10L, ChatRoomType.DIRECT, null, 1L,
                List.of(2L), "지예", LocalDateTime.of(2026, 9, 1, 15, 0)));

        then(pushSendService).should().sendToUsers(
                List.of(2L), NotificationType.CHAT, Map.of("chatRoomId", "10"), "지예");
    }

    @Test
    @DisplayName("발신자를 차단한 수신자는 CHAT 푸시 대상에서 제외된다(#408)")
    void givenBlockerRecipient_whenHandle_thenExcludedFromPush() {
        allowDelivery();
        given(blockReader.getBlockerUserIds(1L)).willReturn(Set.of(2L));

        listener.handle(new ChatMessageSentEvent(
                20L, 10L, ChatRoomType.GROUP, 5L, 1L,
                List.of(2L, 3L), "지예", LocalDateTime.of(2026, 9, 9, 15, 0)));

        then(pushSendService).should().sendToUsers(
                List.of(3L), NotificationType.CHAT, Map.of("chatRoomId", "10"), "지예");
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
        return new ChatFeatureProperties(null, new ChatFeatureProperties.ReplyPush(enabled), 999L);
    }

    private static ChatMessageSentEvent replyEvent(Long replyToSender) {
        return new ChatMessageSentEvent(20L, 10L, ChatRoomType.GROUP, 5L, 1L,
                List.of(2L, 3L, 4L), "윤슬", LocalDateTime.of(2026, 9, 29, 15, 0), replyToSender);
    }

    @Test
    @DisplayName("답장 푸시 ON: 원문 주인은 CHAT_REPLY 1건만, 나머지는 CHAT (같은 사람에게 두 번 안 감, #456)")
    void givenReplyPushOn_whenReply_thenOwnerGetsReplyOthersGetChat() {
        listener = new ChatMessageSentEventListener(pushSendService, blockReader, chatDeliveryService, replyPush(true));
        allowDelivery();

        listener.handle(replyEvent(3L));

        then(pushSendService).should().sendToUsers(
                List.of(3L), NotificationType.CHAT_REPLY, Map.of("chatRoomId", "10"), "윤슬");
        then(pushSendService).should().sendToUsers(
                List.of(2L, 4L), NotificationType.CHAT, Map.of("chatRoomId", "10"), "윤슬");
        then(pushSendService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("답장 푸시 OFF: 원문 주인 포함 전부 기존 CHAT (#456)")
    void givenReplyPushOff_whenReply_thenAllGetChat() {
        listener = new ChatMessageSentEventListener(pushSendService, blockReader, chatDeliveryService, replyPush(false));
        allowDelivery();

        listener.handle(replyEvent(3L));

        then(pushSendService).should().sendToUsers(
                List.of(2L, 3L, 4L), NotificationType.CHAT, Map.of("chatRoomId", "10"), "윤슬");
        then(pushSendService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("원문 주인이 수신자가 아니면(운영 글 시스템 유저, 본인, 차단) 답장 푸시 없이 CHAT 만 (#456)")
    void givenReplyToNonRecipient_whenReply_thenNoReplyPush() {
        listener = new ChatMessageSentEventListener(pushSendService, blockReader, chatDeliveryService, replyPush(true));
        allowDelivery();

        listener.handle(replyEvent(999L));

        then(pushSendService).should().sendToUsers(
                List.of(2L, 3L, 4L), NotificationType.CHAT, Map.of("chatRoomId", "10"), "윤슬");
        then(pushSendService).shouldHaveNoMoreInteractions();
    }

    private void allowDelivery() {
        willAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).given(chatDeliveryService).deliverIfActive(eq(10L), any(Runnable.class));
    }
}
