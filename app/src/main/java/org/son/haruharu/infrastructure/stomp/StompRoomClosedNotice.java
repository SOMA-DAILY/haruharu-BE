package org.son.haruharu.infrastructure.stomp;

/** 인증된 세션이 종료된 채팅을 사용하려 한 경우의 업무 알림. 프로토콜 오류로 소켓을 닫지 않는다. */
public record StompRoomClosedNotice(String sessionId, String userName, Long chatRoomId) {}
