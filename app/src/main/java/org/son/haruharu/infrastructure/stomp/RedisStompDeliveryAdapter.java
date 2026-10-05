package org.son.haruharu.infrastructure.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * STOMP 전달을 이 인스턴스에 직접 하고, Redis Pub/Sub 로 다른 인스턴스에 퍼뜨린다(#453).
 * <p>
 * 자기 인스턴스 세션(발신자 본인 포함)은 Redis 상태와 무관하게 로컬로 즉시 받는다. 발행 envelope 에 origin 을 실어
 * 되돌아온 자기 메시지는 구독 리스너가 버린다 — 발행 성공 여부·구독자 수로 자기 구독 상태를 추정하지 않는다
 * (발행 연결과 구독 연결은 별개라 다른 인스턴스만 받아도 구독자 수가 1 이상이다).
 * <p>
 * 발행은 전용 단일 스레드에 넘기고 즉시 반환한다. 호출자는 채팅방 행 잠금·DB 커넥션을 쥔 커밋 후 리스너라
 * Redis 가 느려져도 잠금을 붙잡지 않게 한다.
 * <p>
 * 순서는 인스턴스 안에서만 보장된다 — 이 인스턴스의 발행 대기열과 다른 인스턴스의 로컬 전달 사이에는 순서가 없어,
 * 이 인스턴스가 발행한 메시지가 다른 인스턴스에서 닫힌 방의 종료 알림보다 늦게 도착할 수 있다. 그래서 방 메시지는
 * guard 를 실어 보내고, 수신 인스턴스가 전달 직전에 방 활성을 다시 확인한다({@link StompDeliveryGuard}).
 * <p>
 * payload 는 Boot 의 {@link JsonMapper}(spring.jackson 설정 = snake_case)로 한 번만 JSON 트리로 바꾼다.
 * 매퍼를 새로 만들면 camelCase 로 깨진다. 발행 실패는 다른 인스턴스 세션만 실시간을 놓친다(재조회로 보완).
 */
@Slf4j
@Primary
@Component
@ConditionalOnProperty(prefix = "haruharu.chat.fanout.redis", name = "enabled", havingValue = "true")
public class RedisStompDeliveryAdapter implements StompDeliveryPort {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final ChannelTopic chatFanoutTopic;
    private final LocalStompDeliveryAdapter localDelivery;
    private final StompFanoutRuntime runtime;

    public RedisStompDeliveryAdapter(StringRedisTemplate redisTemplate, JsonMapper jsonMapper,
                                     ChannelTopic chatFanoutTopic, LocalStompDeliveryAdapter localDelivery,
                                     StompFanoutRuntime runtime) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.chatFanoutTopic = chatFanoutTopic;
        this.localDelivery = localDelivery;
        this.runtime = runtime;
    }

    @Override
    public void sendToTopic(String topic, Object payload) {
        localDelivery.sendToTopic(topic, payload);
        publishAsync(StompFanoutEnvelope.Kind.TOPIC, null, topic, null, payload);
    }

    @Override
    public void sendToTopic(String topic, Object payload, String guard) {
        localDelivery.sendToTopic(topic, payload);
        publishAsync(StompFanoutEnvelope.Kind.TOPIC, null, topic, guard, payload);
    }

    @Override
    public void sendToUser(String userId, String topic, Object payload) {
        localDelivery.sendToUser(userId, topic, payload);
        publishAsync(StompFanoutEnvelope.Kind.USER, userId, topic, null, payload);
    }

    /** 직렬화는 호출 스레드에서(CPU 만), 네트워크 발행은 전용 스레드에서 한다. 어떤 실패도 호출자에게 전파하지 않는다. */
    private void publishAsync(StompFanoutEnvelope.Kind kind, String userId, String topic, String guard,
                              Object payload) {
        try {
            String json = jsonMapper.writeValueAsString(new StompFanoutEnvelope(
                    runtime.instanceId(), kind, topic, userId, guard, jsonMapper.valueToTree(payload)));
            runtime.publishExecutor().execute(() -> publish(kind, topic, json));
        } catch (RuntimeException e) {
            log.error("STOMP fanout publish 실패: kind={} topic={} cause={}", kind, topic, e.toString());
        }
    }

    private void publish(StompFanoutEnvelope.Kind kind, String topic, String json) {
        try {
            redisTemplate.convertAndSend(chatFanoutTopic.getTopic(), json);
        } catch (RuntimeException e) {
            // 장애 중엔 메시지마다 찍히므로 스택트레이스 없이 원인만 남긴다. 문구는 Datadog 로그 모니터 기준.
            log.error("STOMP fanout publish 실패: kind={} topic={} cause={}", kind, topic, e.toString());
        }
    }
}
