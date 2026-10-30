package org.son.haruharu.infrastructure.stomp;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.son.haruharu.domain.chat.application.ChatSubscriptionReconciler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatSubscriptionLifecycleTest {
    private ChatSubscriptionRegistry registry() {
        var active = mock(ChatDeliveryService.class);
        when(active.isActive(anyLong())).thenReturn(true);
        return new ChatSubscriptionRegistry(active, mock(ApplicationEventPublisher.class));
    }

    @Test
    void duplicateSubscriptionIdCannotLeaveAnUntrackedDestinationCacheEntry() {
        var registry = registry();
        assertThat(subscriptions(registry, "/sub/chat-rooms/20")).isEmpty();
        registry.registerSubscription(frame(SimpMessageType.SUBSCRIBE, "/sub/chat-rooms/10", "a"));
        registry.registerSubscription(frame(SimpMessageType.SUBSCRIBE, "/sub/chat-rooms/20", "a"));
        assertThat(subscriptions(registry, "/sub/chat-rooms/20")).isEmpty();
        registry.closeRoom(10L, () -> {});
        assertThat(subscriptions(registry, "/sub/chat-rooms/10")).isEmpty();
        assertThat(subscriptions(registry, "/sub/chat-rooms/20")).isEmpty();
        assertThat(registry.roomIds()).isEmpty();
    }

    @Test
    void subscriptionDelayedPastClosureCannotRegisterAgain() {
        var active = mock(ChatDeliveryService.class);
        when(active.isActive(10L)).thenReturn(true);
        var registry = new ChatSubscriptionRegistry(active, mock(ApplicationEventPublisher.class));
        registry.registerSubscription(frame(SimpMessageType.SUBSCRIBE, "/sub/chat-rooms/10", "a"));
        when(active.isActive(10L)).thenReturn(false);
        registry.closeRoom(10L, () -> {});
        // inbound 인가를 먼저 통과하고 broker 등록만 늦게 실행된 프레임을 재현한다.
        registry.registerSubscription(frame(SimpMessageType.SUBSCRIBE, "/sub/chat-rooms/10/events", "b"));
        assertThat(subscriptions(registry, "/sub/chat-rooms/10/events")).isEmpty();
        assertThat(registry.roomIds()).isEmpty();
    }

    @Test
    void missedClosureIsRecoveredAndOnlyClosedRoomSubscriptionsAreRemoved() {
        var registry = registry();
        registry.registerSubscription(frame(SimpMessageType.SUBSCRIBE, "/sub/chat-rooms/10", "a"));
        registry.registerSubscription(frame(SimpMessageType.SUBSCRIBE, "/sub/chat-rooms/10/events", "b"));
        registry.registerSubscription(frame(SimpMessageType.SUBSCRIBE, "/sub/chat-rooms/20", "c"));
        registry.registerSubscription(frame(SimpMessageType.SUBSCRIBE, "/queue/errors-users1", "d"));
        var delivery = mock(ChatDeliveryService.class);
        when(delivery.isActive(20L)).thenReturn(true);
        var messaging = mock(SimpMessagingTemplate.class);
        var events = mock(ApplicationEventPublisher.class);
        var closer = new ChatSubscriptionCloser(registry, messaging, events);
        var reconciler = new ChatSubscriptionReconciler(registry, closer, delivery);
        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(subscriptions(registry, "/sub/chat-rooms/10")).isEmpty();
        assertThat(subscriptions(registry, "/sub/chat-rooms/10/events")).isEmpty();
        assertThat(subscriptions(registry, "/sub/chat-rooms/20")).containsExactly("c");
        assertThat(subscriptions(registry, "/queue/errors-users1")).containsExactly("d");
        verify(messaging, times(2)).convertAndSend(anyString(), any(Object.class), any(java.util.Map.class));
        verify(events, times(2)).publishEvent(any(org.springframework.context.ApplicationEvent.class));
    }

    @Test
    void failedNotificationRetainsSubscriptionsForRetry() {
        var registry = registry();
        registry.registerSubscription(frame(SimpMessageType.SUBSCRIBE, "/sub/chat-rooms/10", "a"));
        assertThatThrownBy(() -> registry.closeRoom(10L, () -> { throw new IllegalStateException(); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(subscriptions(registry, "/sub/chat-rooms/10")).containsExactly("a");
        registry.closeRoom(10L, () -> {});
        assertThat(registry.roomIds()).isEmpty();
    }

    @Test
    void notificationRoutesBeforeUnsubscribeAndDisconnectReleasesTracking() {
        var registry = registry();
        registry.registerSubscription(frame(SimpMessageType.SUBSCRIBE, "/sub/chat-rooms/10", "a"));
        List<String> routed = new ArrayList<>();
        registry.closeRoom(10L, () -> routed.addAll(subscriptions(registry, "/sub/chat-rooms/10")));
        assertThat(routed).containsExactly("a");
        registry.registerSubscription(frame(SimpMessageType.SUBSCRIBE, "/sub/chat-rooms/20", "a"));
        registry.unregisterAllSubscriptions("s1");
        assertThat(registry.roomIds()).isEmpty();
    }

    @Test
    void queuedOutboundIsRecheckedAndNativeHeaderCannotBypassGuard() {
        var delivery = mock(ChatDeliveryService.class);
        var registry = mock(ChatSubscriptionRegistry.class);
        when(registry.recipient(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of((java.security.Principal)()->"1"));
        var guard = new ChatOutboundChannelInterceptor(delivery,registry);
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination("/sub/chat-rooms/10");
        headers.setNativeHeader(ChatSubscriptionCloser.CLOSED_NOTIFICATION, "true");
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThat(guard.beforeHandle(message, null, null)).isNull();
        when(delivery.isActiveFor(10L,1L)).thenReturn(true);
        assertThat(guard.beforeHandle(message, null, null)).isSameAs(message);
        when(delivery.isActiveFor(10L,1L)).thenThrow(new IllegalStateException("database unavailable"));
        assertThat(guard.beforeHandle(message, null, null)).isNull();
        headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination("/sub/chat-rooms/10");
        headers.setHeader(ChatSubscriptionCloser.CLOSED_NOTIFICATION, true);
        var closed = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThat(guard.beforeHandle(closed, null, null)).isSameAs(closed);
    }

    private List<String> subscriptions(ChatSubscriptionRegistry registry, String topic) {
        return registry.findSubscriptions(frame(SimpMessageType.MESSAGE, topic, null))
                .values().stream().flatMap(List::stream).toList();
    }

    private Message<byte[]> frame(SimpMessageType type, String destination, String id) {
        var headers = SimpMessageHeaderAccessor.create(type);
        headers.setSessionId("s1");
        headers.setSubscriptionId(id);
        headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
