package org.son.haruharu.domain.chat.business.event;

import java.util.List;

/**
 * 채팅 메시지 전송 시 발행(#60). 수신자 계산은 chat 도메인이 담당해 recipientUserIds(발신자 제외 전원)로 전달한다.
 * DIRECT는 상대 1명, GROUP은 나 제외 멤버 전원. senderNickname은 CHAT 푸시 본문("OO님이 메시지를 보냈어요") 치환용
 * — 대화 내용은 프라이버시상 미포함. notification 리스너가 커밋 후 CHAT 푸시를 발송한다.
 */
public record ChatMessageSentEvent(Long chatRoomId, List<Long> recipientUserIds, String senderNickname) {}
