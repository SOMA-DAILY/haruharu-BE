package org.son.haruharu.infrastructure.stomp;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Objects;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.DefaultSubscriptionRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/** 실제 broker 등록과 동일한 생명주기로 추적한다. 세션 이벤트만 추적하면 비동기 등록과 경합한다. */
@Component
@RequiredArgsConstructor
public class ChatSubscriptionRegistry extends DefaultSubscriptionRegistry {
    private static final Pattern TOPIC = Pattern.compile("/sub/chat-rooms/(\\d+)(?:/events)?");
    private final Map<Key, Subscription> subscriptions = new HashMap<>();
    private final ChatDeliveryService delivery;
    private final ApplicationEventPublisher events;

    public static Long roomId(String destination) {
        var matcher = TOPIC.matcher(destination == null ? "" : destination);
        if (!matcher.matches()) return null;
        try { return Long.parseLong(matcher.group(1)); }
        catch (NumberFormatException ignored) { return null; }
    }

    @Override
    protected synchronized void addSubscriptionInternal(String session, String id, String destination, Message<?> message) {
        // Spring의 destination cache는 중복 id에도 갱신될 수 있어, delegate 호출 전에 막는다.
        if (subscriptions.containsKey(new Key(session, id))) return;
        Long roomId = roomId(destination);
        if (roomId != null && !delivery.isActive(roomId)) {
            Principal user = SimpMessageHeaderAccessor.getUser(message.getHeaders());
            if (user != null) events.publishEvent(new StompRoomClosedNotice(session, user.getName(), roomId));
            return;
        }
        super.addSubscriptionInternal(session, id, destination, message);
        subscriptions.putIfAbsent(new Key(session, id), new Subscription(session, id, destination,
                SimpMessageHeaderAccessor.getUser(message.getHeaders())));
    }

    @Override
    protected synchronized void removeSubscriptionInternal(String session, String id, Message<?> message) {
        super.removeSubscriptionInternal(session, id, message);
        subscriptions.remove(new Key(session, id));
    }

    @Override
    public synchronized void unregisterAllSubscriptions(String session) {
        super.unregisterAllSubscriptions(session);
        subscriptions.keySet().removeIf(key -> key.session().equals(session));
    }

    public synchronized Set<Long> roomIds() {
        return subscriptions.values().stream().map(s -> roomId(s.destination()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /** 알림 라우팅 후 같은 잠금 안에서 해당 방의 두 토픽만 제거한다. 실패하면 다음 sweep에서 재시도한다. */
    public synchronized List<Subscription> closeRoom(Long roomId, Runnable notifySubscribers) {
        List<Subscription> closing = subscriptions.values().stream()
                .filter(s -> roomId.equals(roomId(s.destination()))).toList();
        notifySubscribers.run();
        closing.forEach(s -> unregisterSubscription(s.unsubscribeMessage()));
        return closing;
    }

    private record Key(String session, String id) {}

    public record Subscription(String session, String id, String destination, Principal user) {
        public Message<byte[]> unsubscribeMessage() {
            var headers = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
            headers.setSessionId(session);
            headers.setSubscriptionId(id);
            headers.setUser(user);
            return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        }
    }
}
