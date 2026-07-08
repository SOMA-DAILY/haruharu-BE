package org.son.haruharu.domain.chat.business.event;

/**
 * 채팅 메시지 전송 시 발행(#60). 수신자(상대) 계산은 chat 도메인이 담당해 recipientUserId로 전달한다.
 * senderNickname은 CHAT 푸시 본문("OO님이 메시지를 보냈어요") 치환용 — 대화 내용은 프라이버시상 미포함.
 * notification 리스너가 커밋 후 CHAT 푸시를 발송한다.
 */
public record ChatMessageSentEvent(Long chatRoomId, Long recipientUserId, String senderNickname) {}
