package org.son.haruharu.infrastructure.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 이 인스턴스의 simpleBroker 로 직접 전달한다(#453). 항상 등록된다.
 * <p>
 * 팬아웃 토글 off 면 Port 의 유일한 구현이고, on 이면 {@link RedisStompDeliveryAdapter}(@Primary)가 Port 를 맡고
 * 이 어댑터는 Redis 수신 후 로컬 전달·발행 실패 시 대체 경로로 쓰인다.
 */
@Slf4j
@Component
public class LocalStompDeliveryAdapter implements StompDeliveryPort {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatSubscriptionCloser closer;

    public LocalStompDeliveryAdapter(SimpMessagingTemplate messagingTemplate, ChatSubscriptionCloser closer) {
        this.messagingTemplate = messagingTemplate;
        this.closer = closer;
    }

    @Override
    public void closeRoom(Long chatRoomId, Object payload) {
        closer.close(chatRoomId, payload);
    }

    @Override
    public void sendToTopic(String topic, Object payload) {
        try {
            messagingTemplate.convertAndSend(topic, payload);
        } catch (RuntimeException e) {
            log.error("STOMP 로컬 전달 실패: topic={} cause={}", topic, e.toString());
        }
    }

    /** 로컬은 호출자가 이미 조건을 확인한 상태라 guard 없이 전달한다. */
    @Override
    public void sendToTopic(String topic, Object payload, String guard) {
        sendToTopic(topic, payload);
    }

    @Override
    public void sendToUser(String userId, String topic, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(userId, topic, payload);
        } catch (RuntimeException e) {
            log.error("STOMP 로컬 전달 실패: userId={} topic={} cause={}", userId, topic, e.toString());
        }
    }
}
