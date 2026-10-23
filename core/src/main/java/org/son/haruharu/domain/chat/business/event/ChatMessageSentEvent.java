package org.son.haruharu.domain.chat.business.event;

import java.time.LocalDateTime;
import java.util.List;

import org.son.haruharu.domain.chat.implement.ChatRoomType;

/**
 * 채팅 메시지 전송 시 발행(#60). 수신자 계산은 chat 도메인이 담당해 recipientUserIds(발신자 제외 전원)로 전달한다.
 * DIRECT는 상대 1명, GROUP은 나 제외 멤버 전원. senderNickname은 CHAT 푸시 본문("OO님이 메시지를 보냈어요") 치환용
 * — 대화 내용은 프라이버시상 미포함. notification 리스너가 커밋 후 CHAT 푸시를 발송한다.
 * replyToSenderUserId(#456): 답장이면 원문 보낸 사람. 리스너가 reply-push 플래그 ON 일 때 그 사람에게만 CHAT_REPLY 를 보낸다.
 */
public record ChatMessageSentEvent(
        Long chatMessageId,
        Long chatRoomId,
        ChatRoomType chatRoomType,
        Long roomId,
        Long senderUserId,
        List<Long> recipientUserIds,
        String senderNickname,
        LocalDateTime occurredAt,
        Long replyToSenderUserId,
        boolean photo
) {
    public ChatMessageSentEvent(Long chatMessageId, Long chatRoomId, ChatRoomType chatRoomType, Long roomId,
                                Long senderUserId, List<Long> recipientUserIds, String senderNickname,
                                LocalDateTime occurredAt, Long replyToSenderUserId) {
        this(chatMessageId, chatRoomId, chatRoomType, roomId, senderUserId, recipientUserIds, senderNickname,
                occurredAt, replyToSenderUserId, false);
    }

    /** 답장 이전(#456) 8-arg 호환. 답장 아님. */
    public ChatMessageSentEvent(Long chatMessageId, Long chatRoomId, ChatRoomType chatRoomType, Long roomId,
                                Long senderUserId, List<Long> recipientUserIds, String senderNickname,
                                LocalDateTime occurredAt) {
        this(chatMessageId, chatRoomId, chatRoomType, roomId, senderUserId, recipientUserIds, senderNickname,
                occurredAt, null);
    }
}
