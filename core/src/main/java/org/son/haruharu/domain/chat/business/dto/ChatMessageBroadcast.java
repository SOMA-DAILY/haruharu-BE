package org.son.haruharu.domain.chat.business.dto;

import org.son.haruharu.domain.chat.implement.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 구독자(/sub)에게 브로드캐스트되는 메시지 payload. 필드는 추가만 한다(지금 앱은 모르는 키를 버린다).
 * reactions/my_reaction/reply_to(#456): 방금 보낸 메시지라 반응은 비어 있고, 수신자별 값(my_reaction)은 null.
 */
public record ChatMessageBroadcast(
        Long messageId,
        Long chatRoomId,
        Long senderId,
        String content,
        LocalDateTime sentAt,
        Long videoPostId,
        List<ReactionCountItem> reactions,
        String myReaction,
        ReplyToView replyTo
) {
    public ChatMessageBroadcast(Long messageId, Long chatRoomId, Long senderId, String content, LocalDateTime sentAt) {
        this(messageId, chatRoomId, senderId, content, sentAt, null);
    }

    public ChatMessageBroadcast(Long messageId, Long chatRoomId, Long senderId, String content, LocalDateTime sentAt,
                                Long videoPostId) {
        this(messageId, chatRoomId, senderId, content, sentAt, videoPostId, List.of(), null, null);
    }

    public static ChatMessageBroadcast from(ChatMessage message) {
        return new ChatMessageBroadcast(
                message.id(), message.chatRoomId(), message.senderId(), message.content(), message.createdAt(),
                message.videoPostId(), List.of(), null, replyOf(message));
    }

    /** 방금 저장된 답장의 인용. 원문은 저장 직전에 검증했으므로 available=true. */
    private static ReplyToView replyOf(ChatMessage message) {
        if (message.replyToMessageId() == null) {
            return null;
        }
        return ReplyToView.of(message.replyToMessageId(), message.replyPreviewSender(),
                message.replyPreviewContent(), true);
    }
}
