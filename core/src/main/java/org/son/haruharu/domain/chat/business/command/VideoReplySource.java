package org.son.haruharu.domain.chat.business.command;

/**
 * 영상 답장을 쓰기 전에 본 추천(#558). 앱이 그대로 전달한 값이라 검증 전이다.
 * 잘못된 값이어도 답장 저장은 막지 않고, 추천 연결만 빠진다.
 */
public record VideoReplySource(String setId, String cardId, String sessionId) {}
