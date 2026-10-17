package org.son.haruharu.infrastructure.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 실제 Redis(Valkey) Pub/Sub 로 팬아웃 배선을 검증한다(#453).
 * <p>
 * 자기 인스턴스 발행은 로컬로 한 번만 전달되고(채널에서 되돌아온 메시지는 origin 으로 버림), 다른 인스턴스가 발행한
 * envelope 는 구독 리스너를 거쳐 JSON 트리로 로컬 전달된다. 로컬 어댑터를 목으로 바꿔 도착을 관찰한다.
 */
@SpringBootTest(properties = "haruharu.chat.fanout.redis.enabled=true")
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StompRedisFanoutIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("valkey/valkey:8-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @MockitoBean LocalStompDeliveryAdapter localDelivery;
    @MockitoBean StompDeliveryGuard deliveryGuard;
    @Autowired StompDeliveryPort deliveryPort;
    @Autowired RedisMessageListenerContainer listenerContainer;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired ChannelTopic chatFanoutTopic;
    @Autowired JsonMapper jsonMapper;

    private final ChatMessageBroadcast broadcast =
            new ChatMessageBroadcast(1024L, 12L, 102L, "안녕", LocalDateTime.of(2026, 9, 26, 21, 0));

    @BeforeEach
    void awaitSubscription() throws InterruptedException {
        // 구독은 기동 후 백그라운드에서 맺어지므로 완료를 기다린다.
        long deadline = System.currentTimeMillis() + 10_000;
        while (!listenerContainer.isListening() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(listenerContainer.isListening()).isTrue();
        clearInvocations(localDelivery, deliveryGuard);
    }

    @Test
    @DisplayName("팬아웃 토글이 켜지면 STOMP 전달은 Redis 어댑터가 맡는다")
    void givenFanoutEnabled_whenInjectPort_thenRedisAdapter() {
        assertThat(deliveryPort).isInstanceOf(RedisStompDeliveryAdapter.class);
    }

    @Test
    @DisplayName("자기 인스턴스 발행은 원본 그대로 로컬에 한 번만 전달되고, 채널에서 되돌아온 메시지는 버린다")
    void givenOwnSend_whenPublishedThroughRedis_thenLocalDeliveredOnceWithoutEcho() {
        // when
        deliveryPort.sendToTopic("/sub/chat-rooms/12", broadcast);

        // then: 되돌아온 envelope 까지 처리될 시간을 둔 뒤에도 원본 1회뿐
        verify(localDelivery, after(1_000).times(1)).sendToTopic(anyString(), any());
        verify(localDelivery).sendToTopic("/sub/chat-rooms/12", broadcast);
    }

    @Test
    @DisplayName("다른 인스턴스가 발행한 방 목적지 전달은 구독 리스너를 거쳐 JSON 트리로 한 번 로컬 전달된다")
    void givenForeignTopicEnvelope_whenReceivedFromRedis_thenDeliveredOnceAsJsonTree() {
        // when
        publishForeign(StompFanoutEnvelope.Kind.TOPIC, null, "/sub/chat-rooms/12");

        // then
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(localDelivery, timeout(5_000)).sendToTopic(eq("/sub/chat-rooms/12"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(JsonNode.class);
        assertThat(((JsonNode) payload.getValue()).get("message_id").asLong()).isEqualTo(1024L);
        verify(localDelivery, after(500).times(1)).sendToTopic(anyString(), any());
    }

    @Test
    @DisplayName("다른 인스턴스가 발행한 사용자 전달은 대상 사용자에게 한 번 로컬 전달된다")
    void givenForeignUserEnvelope_whenReceivedFromRedis_thenDeliveredOnceToUser() {
        // when
        publishForeign(StompFanoutEnvelope.Kind.USER, "102", "/queue/chat-room-events");

        // then
        verify(localDelivery, timeout(5_000))
                .sendToUser(eq("102"), eq("/queue/chat-room-events"), any(JsonNode.class));
        verify(localDelivery, after(500).times(1)).sendToUser(anyString(), anyString(), any());
        verify(localDelivery, never()).sendToTopic(anyString(), any());
    }

    @Test
    @DisplayName("다른 인스턴스 방 메시지는 도착 시점에 방이 닫혔으면(guard 불통과) 전달하지 않는다")
    void givenForeignGuardedMessageForClosedRoom_whenReceived_thenDropped() {
        // given
        given(deliveryGuard.allows("chat-room-active:12")).willReturn(false);

        // when
        publishForeign(StompFanoutEnvelope.Kind.TOPIC, null, "/sub/chat-rooms/12", "chat-room-active:12");

        // then
        verify(deliveryGuard, timeout(5_000)).allows("chat-room-active:12");
        verify(localDelivery, after(500).never()).sendToTopic(anyString(), any());
    }

    @Test
    @DisplayName("다른 인스턴스 방 메시지는 도착 시점에 방이 활성이면(guard 통과) 전달한다")
    void givenForeignGuardedMessageForActiveRoom_whenReceived_thenDelivered() {
        // given
        given(deliveryGuard.allows("chat-room-active:12")).willReturn(true);

        // when
        publishForeign(StompFanoutEnvelope.Kind.TOPIC, null, "/sub/chat-rooms/12", "chat-room-active:12");

        // then
        verify(localDelivery, timeout(5_000)).sendToTopic(eq("/sub/chat-rooms/12"), any(JsonNode.class));
    }

    private void publishForeign(StompFanoutEnvelope.Kind kind, String userId, String destination) {
        publishForeign(kind, userId, destination, null);
    }

    @Test
    @DisplayName("다른 인스턴스의 종료 이벤트는 활성 전달 가드 없이 로컬 구독 정리를 실행한다")
    void foreignClosureRemovesLocalSubscriptionsEvenAfterRoomBecameInactive() {
        var payload = jsonMapper.valueToTree(new org.son.haruharu.domain.chat.application.ChatLifecycleEventListener
                .RoomClosedPayload("CHAT_ROOM_CLOSED", 12L));
        redisTemplate.convertAndSend(chatFanoutTopic.getTopic(), jsonMapper.writeValueAsString(
                new StompFanoutEnvelope("instance-other", StompFanoutEnvelope.Kind.ROOM_CLOSED,
                        "12", null, null, payload)));
        ArgumentCaptor<Object> received = ArgumentCaptor.forClass(Object.class);
        verify(localDelivery, timeout(5_000)).closeRoom(eq(12L), received.capture());
        assertThat(((JsonNode) received.getValue()).get("type").asText()).isEqualTo("CHAT_ROOM_CLOSED");
        assertThat(((JsonNode) received.getValue()).get("chat_room_id").asLong()).isEqualTo(12L);
        verify(deliveryGuard, never()).allows(anyString());
    }

    private void publishForeign(StompFanoutEnvelope.Kind kind, String userId, String destination, String guard) {
        JsonNode payload = jsonMapper.valueToTree(Map.of("message_id", 1024));
        String json = jsonMapper.writeValueAsString(
                new StompFanoutEnvelope("instance-other", kind, destination, userId, guard, payload));
        redisTemplate.convertAndSend(chatFanoutTopic.getTopic(), json);
    }
}
