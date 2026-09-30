package org.son.haruharu.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 설정.
 * - 핸드셰이크 엔드포인트: /ws-chat (JWT 인증은 StompAuthChannelInterceptor의 CONNECT 처리에서)
 * - 구독: /sub/**(토픽), /user/queue/**(개인 큐 — 에러 프레임), 발행: /pub/**
 * - SimpleBroker(인메모리) 사용 — 구독은 인스턴스별. 다중 인스턴스는 Redis Pub/Sub 팬아웃으로 전달한다
 *   (haruharu.chat.fanout.redis.enabled, #453 — infrastructure/stomp).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // "/queue" 미등록 시 @SendToUser("/queue/errors")가 변환한 /queue/errors-user{session}을
        // SimpleBroker가 처리하지 않고 조용히 드랍한다(#168) — 에러 프레임이 클라이언트에 도달하지 못함.
        registry.enableSimpleBroker("/sub", "/queue");
        registry.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
