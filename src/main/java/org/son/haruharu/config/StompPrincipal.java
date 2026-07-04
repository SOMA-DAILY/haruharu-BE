package org.son.haruharu.config;

import java.security.Principal;

/**
 * STOMP 세션에 부여하는 인증 주체. userId(Long)를 담아 메시지 핸들러에서 senderId로 사용한다.
 * getName()은 Spring의 사용자 세션 레지스트리 키로 쓰이므로 userId 문자열을 반환한다.
 */
public record StompPrincipal(Long userId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
