package org.son.haruharu.infrastructure.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisStompDeliveryAdapterTest {

    private static final String CHANNEL = "chat:broadcast";
    private static final String ROOM_TOPIC = "/sub/chat-rooms/12";
    private static final String INSTANCE_ID = "instance-a";

    // 운영 매퍼(spring.jackson.property-naming-strategy=SNAKE_CASE)와 같은 규칙
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Mock StringRedisTemplate redisTemplate;
    @Mock LocalStompDeliveryAdapter localDelivery;
    RedisStompDeliveryAdapter adapter;

    private final ChatMessageBroadcast broadcast =
            new ChatMessageBroadcast(1024L, 12L, 102L, "안녕", LocalDateTime.of(2026, 9, 26, 21, 0));

    @BeforeEach
    void setUp() {
        // 발행 실행기를 동기로 바꿔 발행 결과를 바로 검증한다
        StompFanoutRuntime runtime = new StompFanoutRuntime(INSTANCE_ID, new SyncTaskExecutor(), new SyncTaskExecutor());
        adapter = new RedisStompDeliveryAdapter(redisTemplate, jsonMapper, new ChannelTopic(CHANNEL), localDelivery, runtime);
    }

    @Test
    @DisplayName("방 목적지 전달은 자기 인스턴스에 직접 전달하고, origin 을 실은 envelope 를 채널에 발행한다")
    void givenTopicSend_whenSendToTopic_thenDeliversLocallyAndPublishesEnvelope() {
        // when
        adapter.sendToTopic(ROOM_TOPIC, broadcast);

        // then
        verify(localDelivery).sendToTopic(ROOM_TOPIC, broadcast);
        JsonNode envelope = capturePublishedEnvelope();
        assertThat(envelope.get("origin").asString()).isEqualTo(INSTANCE_ID);
        assertThat(envelope.get("kind").asString()).isEqualTo("TOPIC");
        assertThat(envelope.get("destination").asString()).isEqualTo(ROOM_TOPIC);
        assertThat(envelope.get("payload").get("message_id").asLong()).isEqualTo(1024L);
        assertThat(envelope.get("payload").get("content").asString()).isEqualTo("안녕");
        assertThat(envelope.get("guard").isNull()).isTrue();
    }

    @Test
    @DisplayName("조건부 방 전달은 자기 인스턴스에는 바로 전달하고, envelope 에 guard 를 실어 다른 인스턴스가 재확인하게 한다")
    void givenGuardedTopicSend_whenSendToTopic_thenEnvelopeCarriesGuard() {
        // when
        adapter.sendToTopic(ROOM_TOPIC, broadcast, "chat-room-active:12");

        // then
        verify(localDelivery).sendToTopic(ROOM_TOPIC, broadcast);
        JsonNode envelope = capturePublishedEnvelope();
        assertThat(envelope.get("guard").asString()).isEqualTo("chat-room-active:12");
        assertThat(envelope.get("kind").asString()).isEqualTo("TOPIC");
    }

    @Test
    @DisplayName("사용자 전달은 자기 인스턴스에 직접 전달하고, envelope 에 대상 userId 를 싣는다")
    void givenUserSend_whenSendToUser_thenDeliversLocallyAndEnvelopeCarriesUserId() {
        // when
        adapter.sendToUser("102", "/queue/chat-room-events", broadcast);

        // then
        verify(localDelivery).sendToUser("102", "/queue/chat-room-events", broadcast);
        JsonNode envelope = capturePublishedEnvelope();
        assertThat(envelope.get("kind").asString()).isEqualTo("USER");
        assertThat(envelope.get("user_id").asString()).isEqualTo("102");
        assertThat(envelope.get("destination").asString()).isEqualTo("/queue/chat-room-events");
    }

    @Test
    @DisplayName("Redis 발행이 실패해도 호출자에게 전파하지 않고, 자기 인스턴스 전달은 이미 끝나 있다")
    void givenRedisDown_whenSendToTopic_thenLocalDeliveredWithoutThrowing() {
        // given
        given(redisTemplate.convertAndSend(eq(CHANNEL), anyString()))
                .willThrow(new RedisConnectionFailureException("connection refused"));

        // when & then
        assertThatCode(() -> adapter.sendToTopic(ROOM_TOPIC, broadcast)).doesNotThrowAnyException();
        verify(localDelivery).sendToTopic(ROOM_TOPIC, broadcast);
        then(localDelivery).shouldHaveNoMoreInteractions();
    }

    private JsonNode capturePublishedEnvelope() {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(CHANNEL), json.capture());
        return jsonMapper.readTree(json.getValue());
    }
}
