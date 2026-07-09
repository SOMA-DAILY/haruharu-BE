package org.son.haruharu.config;

import org.springframework.messaging.MessagingException;

/**
 * STOMP 구독 인가 실패(#168). 인증(CONNECT, StompAuthenticationException)과 구분해
 * 클라이언트가 "토큰 문제"가 아니라 "권한 없음"임을 알 수 있게 한다. 해당 프레임만 거부한다.
 */
public class StompAuthorizationException extends MessagingException {
    public StompAuthorizationException() {
        super("구독 권한이 없습니다. 참여 중인 채팅방 토픽만 구독할 수 있습니다.");
    }
}
