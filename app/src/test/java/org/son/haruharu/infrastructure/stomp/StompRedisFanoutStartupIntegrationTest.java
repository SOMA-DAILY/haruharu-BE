package org.son.haruharu.infrastructure.stomp;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Redis 가 없을 때 기동해도 앱이 뜨고, 나중에 Redis 가 살아나면 구독이 자동으로 맺어지는지 검증한다(#453).
 * <p>
 * 컨테이너를 기동 lifecycle 에서 바로 시작하면 첫 연결 실패가 컨텍스트 기동을 실패시켰다 — Redis 장애 중 배포가
 * API 전체 장애로 번지는 회귀를 막는다. 기동 시점엔 비어 있던 고정 포트에 Redis 를 나중에 띄운다.
 */
@SpringBootTest(properties = "haruharu.chat.fanout.redis.enabled=true")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StompRedisFanoutStartupIntegrationTest {

    private static final int REDIS_PORT = freePort();

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("valkey/valkey:8-alpine"))
                    .withExposedPorts(6379)
                    .withCreateContainerCmdModifier(cmd -> cmd.withHostConfig(new HostConfig().withPortBindings(
                            new PortBinding(Ports.Binding.bindPort(REDIS_PORT), new ExposedPort(6379)))));

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> REDIS_PORT);
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    @MockitoBean LocalStompDeliveryAdapter localDelivery;
    @Autowired StompDeliveryPort deliveryPort;
    @Autowired RedisMessageListenerContainer listenerContainer;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired ChannelTopic chatFanoutTopic;
    @Autowired JsonMapper jsonMapper;

    @Test
    @DisplayName("Redis 없이 기동해도 자기 인스턴스 전달은 동작하고, Redis 가 뜨면 구독이 맺어져 다른 인스턴스 전달을 받는다")
    void givenRedisDownAtStartup_whenRedisComesUp_thenSubscribesAndReceives() throws InterruptedException {
        // given: 컨텍스트는 이미 떴다(Redis 없음). 자기 인스턴스 전달은 Redis 와 무관하다.
        assertThat(listenerContainer.isListening()).isFalse();
        deliveryPort.sendToTopic("/sub/chat-rooms/12", Map.of("type", "PING"));
        verify(localDelivery).sendToTopic("/sub/chat-rooms/12", Map.of("type", "PING"));

        // when: Redis 가 살아난다
        REDIS.start();

        // then: 백그라운드 재시도로 구독이 맺어지고 다른 인스턴스 발행을 받는다
        long deadline = System.currentTimeMillis() + 60_000;
        while (!listenerContainer.isListening() && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertThat(listenerContainer.isListening()).isTrue();

        JsonNode payload = jsonMapper.valueToTree(Map.of("message_id", 1));
        redisTemplate.convertAndSend(chatFanoutTopic.getTopic(), jsonMapper.writeValueAsString(
                new StompFanoutEnvelope("instance-other", StompFanoutEnvelope.Kind.TOPIC, "/sub/chat-rooms/12", null,
                        null, payload)));
        verify(localDelivery, timeout(5_000)).sendToTopic(eq("/sub/chat-rooms/12"), any(JsonNode.class));
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
