package org.son.haruharu.infrastructure.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 팬아웃 채널을 구독해 다른 인스턴스가 발행한 STOMP 전달을 이 인스턴스의 세션에 넘긴다(#453).
 * <p>
 * origin 이 자기 인스턴스면 버린다 — 발행 시 이미 로컬 전달했다. guard 가 있으면 전달 직전에 다시 확인해
 * 깨졌으면 버린다(예: 발행 후 다른 인스턴스에서 방이 닫힘). 확인이 실패해도 전달하지 않는다 — 실시간만 놓치고 재조회로 보완된다.
 * payload 는 해석하지 않고 JSON 트리 그대로 로컬 simpleBroker 에 넘긴다 — 변환은 발행 시 한 번만 한다.
 * 이 인스턴스에 해당 목적지 구독자나 사용자 세션이 없으면 simpleBroker 가 그냥 버린다.
 * <p>
 * 컨테이너 스레드에서 호출되므로 예외를 밖으로 던지지 않는다. 잘못된 메시지 하나는 로그만 남기고 건너뛴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "haruharu.chat.fanout.redis", name = "enabled", havingValue = "true")
public class RedisStompSubscribeListener implements MessageListener {

    private final JsonMapper jsonMapper;
    private final LocalStompDeliveryAdapter localDelivery;
    private final StompFanoutRuntime runtime;
    private final StompDeliveryGuard deliveryGuard;

    public RedisStompSubscribeListener(JsonMapper jsonMapper, LocalStompDeliveryAdapter localDelivery,
                                       StompFanoutRuntime runtime, StompDeliveryGuard deliveryGuard) {
        this.jsonMapper = jsonMapper;
        this.localDelivery = localDelivery;
        this.runtime = runtime;
        this.deliveryGuard = deliveryGuard;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            StompFanoutEnvelope envelope = jsonMapper.readValue(message.getBody(), StompFanoutEnvelope.class);
            if (runtime.instanceId().equals(envelope.origin())) {
                return;
            }
            if (envelope.guard() != null && !deliveryGuard.allows(envelope.guard())) {
                return;
            }
            switch (envelope.kind()) {
                case TOPIC -> localDelivery.sendToTopic(envelope.destination(), envelope.payload());
                case USER -> localDelivery.sendToUser(envelope.userId(), envelope.destination(), envelope.payload());
            }
        } catch (RuntimeException e) {
            // 본문은 채팅 내용이라 남기지 않는다
            log.error("STOMP fanout 수신 처리 실패: cause={}", e.toString());
        }
    }
}
