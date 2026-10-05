package org.son.haruharu.infrastructure.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.connection.DefaultMessage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisStompSubscribeListenerTest {

    private static final String INSTANCE_ID = "instance-a";

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Mock LocalStompDeliveryAdapter localDelivery;
    @Mock StompDeliveryGuard deliveryGuard;
    RedisStompSubscribeListener listener;

    @BeforeEach
    void setUp() {
        StompFanoutRuntime runtime = new StompFanoutRuntime(INSTANCE_ID, new SyncTaskExecutor(), new SyncTaskExecutor());
        listener = new RedisStompSubscribeListener(jsonMapper, localDelivery, runtime, deliveryGuard);
    }

    @Test
    @DisplayName("다른 인스턴스의 TOPIC envelope 는 payload 를 해석하지 않고 JSON 트리 그대로 로컬 목적지에 전달한다")
    void givenForeignTopicEnvelope_whenOnMessage_thenDeliversPayloadTreeToTopic() {
        // when
        listener.onMessage(redisMessage("""
                {"origin":"instance-b","kind":"TOPIC","destination":"/sub/chat-rooms/12","user_id":null,
                 "payload":{"message_id":1024,"content":"안녕"}}
                """), null);

        // then
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(localDelivery).sendToTopic(eq("/sub/chat-rooms/12"), payload.capture());
        then(deliveryGuard).shouldHaveNoInteractions(); // guard 없는 전달은 확인하지 않는다
        assertThat(payload.getValue()).isInstanceOf(JsonNode.class);
        assertThat(((JsonNode) payload.getValue()).get("message_id").asLong()).isEqualTo(1024L);
    }

    @Test
    @DisplayName("다른 인스턴스의 USER envelope 는 대상 사용자에게 로컬 전달한다")
    void givenForeignUserEnvelope_whenOnMessage_thenDeliversToUser() {
        // when
        listener.onMessage(redisMessage("""
                {"origin":"instance-b","kind":"USER","destination":"/queue/chat-room-events","user_id":"102",
                 "payload":{"type":"CHAT_ROOM_CLOSED","chat_room_id":12}}
                """), null);

        // then
        verify(localDelivery).sendToUser(eq("102"), eq("/queue/chat-room-events"),
                eq(jsonMapper.readTree("{\"type\":\"CHAT_ROOM_CLOSED\",\"chat_room_id\":12}")));
    }

    @Test
    @DisplayName("guard 가 깨진(방이 닫힌) 다른 인스턴스 메시지는 종료 알림 뒤에 도착하지 않도록 버린다")
    void givenGuardNoLongerHolds_whenOnMessage_thenSkips() {
        // given
        given(deliveryGuard.allows("chat-room-active:12")).willReturn(false);

        // when
        listener.onMessage(redisMessage("""
                {"origin":"instance-b","kind":"TOPIC","destination":"/sub/chat-rooms/12",
                 "guard":"chat-room-active:12","payload":{"message_id":1024}}
                """), null);

        // then
        then(localDelivery).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("guard 가 유지되면 다른 인스턴스 메시지를 로컬 전달한다")
    void givenGuardHolds_whenOnMessage_thenDelivers() {
        // given
        given(deliveryGuard.allows("chat-room-active:12")).willReturn(true);

        // when
        listener.onMessage(redisMessage("""
                {"origin":"instance-b","kind":"TOPIC","destination":"/sub/chat-rooms/12",
                 "guard":"chat-room-active:12","payload":{"message_id":1024}}
                """), null);

        // then
        verify(localDelivery).sendToTopic(eq("/sub/chat-rooms/12"), eq(jsonMapper.readTree("{\"message_id\":1024}")));
    }

    @Test
    @DisplayName("guard 확인이 실패하면 전달하지 않고 예외도 던지지 않는다")
    void givenGuardCheckFails_whenOnMessage_thenSkipsWithoutThrowing() {
        // given
        given(deliveryGuard.allows("chat-room-active:12")).willThrow(new IllegalStateException("db down"));

        // when & then
        assertThatCode(() -> listener.onMessage(redisMessage("""
                {"origin":"instance-b","kind":"TOPIC","destination":"/sub/chat-rooms/12",
                 "guard":"chat-room-active:12","payload":{"message_id":1024}}
                """), null)).doesNotThrowAnyException();
        then(localDelivery).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("자기 인스턴스가 발행한 envelope 는 이미 로컬 전달했으므로 버린다")
    void givenOwnEnvelope_whenOnMessage_thenSkips() {
        // when
        listener.onMessage(redisMessage("""
                {"origin":"instance-a","kind":"TOPIC","destination":"/sub/chat-rooms/12","user_id":null,
                 "payload":{"message_id":1024}}
                """), null);

        // then
        then(localDelivery).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("해석할 수 없는 메시지는 예외 없이 건너뛴다(컨테이너 스레드 보호)")
    void givenMalformedMessage_whenOnMessage_thenSkipsWithoutThrowing() {
        // when & then
        assertThatCode(() -> listener.onMessage(redisMessage("not-json"), null)).doesNotThrowAnyException();
        assertThatCode(() -> listener.onMessage(redisMessage("{\"origin\":\"instance-b\",\"kind\":\"UNKNOWN\"}"), null))
                .doesNotThrowAnyException();
        then(localDelivery).shouldHaveNoInteractions();
    }

    private DefaultMessage redisMessage(String body) {
        return new DefaultMessage("chat:broadcast".getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
    }
}
