package org.son.haruharu.infrastructure.stomp;

/**
 * STOMP 전달 포트(#453). 호출자는 이 인스턴스 로컬 전달인지 Redis 팬아웃인지 모른다.
 * <p>
 * 커밋 후 리스너(채팅방 잠금 안)에서 호출되므로 구현은 실패를 호출자에게 전파하지 않는다.
 */
public interface StompDeliveryPort {

    /** 목적지(예: /sub/chat-rooms/{id})를 구독한 모든 세션에 전달한다. */
    void sendToTopic(String topic, Object payload);

    /**
     * 조건부로 목적지에 전달한다. 이 인스턴스 로컬 전달은 호출자가 이미 조건을 확인했다고 보고 바로 하며,
     * 다른 인스턴스는 전달 직전에 guard 를 {@link StompDeliveryGuard}로 다시 확인해 깨졌으면 버린다.
     */
    void sendToTopic(String topic, Object payload, String guard);

    /** 사용자 세션의 개인 목적지(예: /queue/chat-room-events)로 전달한다. */
    void sendToUser(String userId, String topic, Object payload);
}
