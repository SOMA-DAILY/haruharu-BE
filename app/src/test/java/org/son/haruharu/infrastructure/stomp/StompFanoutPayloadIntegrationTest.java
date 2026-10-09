package org.son.haruharu.infrastructure.stomp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.business.dto.ReactionCountItem;
import org.son.haruharu.domain.chat.business.dto.ReplyToView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 팬아웃(#453)을 거친 STOMP payload 가 로컬 직접 전달과 같은 JSON 인지 실제 Boot 매퍼·브로커 변환기로 검증한다.
 * 발행 측이 설정 없는 매퍼를 새로 만들면(camelCase) 이 테스트가 깨진다. 토글 기본값(off) 컨텍스트에서 돈다.
 */
@SpringBootTest
@ActiveProfiles("test")
class StompFanoutPayloadIntegrationTest {

    @Autowired StompDeliveryPort deliveryPort;
    @Autowired JsonMapper jsonMapper;
    @Autowired @Qualifier("brokerMessageConverter") MessageConverter brokerMessageConverter;

    @Test
    @DisplayName("팬아웃 토글이 꺼져 있으면 STOMP 전달은 인스턴스 로컬 어댑터가 맡는다")
    void givenFanoutDisabled_whenInjectPort_thenLocalAdapter() {
        assertThat(deliveryPort).isInstanceOf(LocalStompDeliveryAdapter.class);
    }

    @Test
    @DisplayName("로컬 어댑터는 실제 브로커로 전달하며 예외를 호출자에게 던지지 않는다")
    void givenFanoutDisabled_whenSendThroughPort_thenDeliversWithoutThrowing() {
        assertThatCode(() -> {
            deliveryPort.sendToTopic("/sub/chat-rooms/12", Map.of("type", "PING"));
            deliveryPort.sendToUser("102", "/queue/chat-room-events", Map.of("type", "PING"));
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis 를 거친 payload 는 클라이언트에게 직접 보낸 것과 같은 snake_case JSON 으로 나간다")
    void givenChatBroadcast_whenRelayedThroughFanout_thenSameJsonAsDirectSend() {
        // given
        // 중첩 객체(reply_to·reactions)와 #456·#457 필드까지 기본값이 아닌 값으로 채워 팬아웃 왕복을 확인한다
        ChatMessageBroadcast broadcast = new ChatMessageBroadcast(1024L, 12L, null, "안녕",
                LocalDateTime.of(2026, 9, 26, 21, 0, 5), 7L,
                List.of(new ReactionCountItem("LAUGH", 3)), null,
                ReplyToView.of(880L, "선우", "원문", true), "TEXT", "하루", false, "SYSTEM");

        // 발행 인스턴스 A: 실제 Boot 매퍼로 envelope 를 만든다(Redis 는 발행 문자열만 캡처)
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        given(redisTemplate.convertAndSend(anyString(), anyString())).willReturn(1L);
        new RedisStompDeliveryAdapter(redisTemplate, jsonMapper, new ChannelTopic("chat:broadcast"),
                mock(LocalStompDeliveryAdapter.class), runtime("instance-a"))
                .sendToTopic("/sub/chat-rooms/12", broadcast);
        ArgumentCaptor<String> published = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("chat:broadcast"), published.capture());

        // 수신 인스턴스 B: 같은 문자열을 받아 로컬로 넘기는 payload 를 캡처
        LocalStompDeliveryAdapter receiverLocal = mock(LocalStompDeliveryAdapter.class);
        new RedisStompSubscribeListener(jsonMapper, receiverLocal, runtime("instance-b"), guard -> true).onMessage(new DefaultMessage(
                "chat:broadcast".getBytes(StandardCharsets.UTF_8),
                published.getValue().getBytes(StandardCharsets.UTF_8)), null);
        ArgumentCaptor<Object> relayed = ArgumentCaptor.forClass(Object.class);
        verify(receiverLocal).sendToTopic(eq("/sub/chat-rooms/12"), relayed.capture());

        // when: 두 payload 를 실제 STOMP 브로커 변환기로 직렬화
        String direct = stompBody(broadcast);
        String viaFanout = stompBody(relayed.getValue());

        // then
        assertThat(viaFanout).isEqualTo(direct);
        assertThat(direct).contains("\"message_id\":1024").contains("\"video_post_id\":7")
                .contains("\"reply_to\":{\"message_id\":880").contains("\"reactions\":[{\"type\":\"LAUGH\",\"count\":3}]")
                .contains("\"message_type\":\"TEXT\"").contains("\"sender_name\":\"하루\"")
                .contains("\"sender_type\":\"SYSTEM\"").contains("\"sender_id\":null");
    }

    private StompFanoutRuntime runtime(String instanceId) {
        return new StompFanoutRuntime(instanceId, new SyncTaskExecutor(), new SyncTaskExecutor());
    }

    private String stompBody(Object payload) {
        byte[] body = (byte[]) brokerMessageConverter.toMessage(payload, null).getPayload();
        return new String(body, StandardCharsets.UTF_8);
    }
}
