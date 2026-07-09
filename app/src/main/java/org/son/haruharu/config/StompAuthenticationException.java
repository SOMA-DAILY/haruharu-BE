package org.son.haruharu.config;

import org.springframework.messaging.MessagingException;

/** STOMP CONNECT 인증 실패. CONNECT 프레임을 거부해 연결을 성립시키지 않는다. */
public class StompAuthenticationException extends MessagingException {
    public StompAuthenticationException() {
        super("STOMP CONNECT 인증에 실패했습니다.");
    }
}
