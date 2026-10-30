package org.son.haruharu.domain.chat.business.dto;

import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMissionReference;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 구독자(/sub)에게 브로드캐스트되는 메시지 payload. REST 목록과 동일한 발신자 계약을 쓴다.
 * reactions/my_reaction/reply_to(#456): 방금 보낸 메시지라 반응은 비어 있고, 수신자별 값(my_reaction)은 null.
 * sender_type=SYSTEM이면 sender_id=null, sender_name='하루'. 내용 유형은 TEXT 또는 MISSION이며, 미션 참조만 공용 방송한다.
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
        ReplyToView replyTo,
        String messageType,
        String senderName,
        boolean deleted,
        String senderType,
        ChatMissionReference mission,
        ChatPhotoView photo,
        String clientRequestId
) {
    public ChatMessageBroadcast(Long messageId, Long chatRoomId, Long senderId, String content, LocalDateTime sentAt, Long videoPostId, List<ReactionCountItem> reactions, String myReaction, ReplyToView replyTo, String messageType, String senderName, boolean deleted, String senderType, ChatMissionReference mission) {
        this(messageId, chatRoomId, senderId, content, sentAt, videoPostId, reactions, myReaction, replyTo, messageType, senderName, deleted, senderType, mission, null, null);
    }

    public ChatMessageBroadcast(Long messageId, Long chatRoomId, Long senderId, String content, LocalDateTime sentAt) {
        this(messageId, chatRoomId, senderId, content, sentAt, null, List.of(), null, null, "TEXT", null, false, "USER", null);
    }

    public ChatMessageBroadcast withPhoto(ChatPhotoView photo, String requestId) {
        return new ChatMessageBroadcast(messageId, chatRoomId, senderId, content, sentAt, videoPostId, reactions, myReaction, replyTo, messageType, senderName, deleted, senderType, mission, deleted ? null : photo, requestId);
    }

    public ChatMessageBroadcast withReplyPhoto(ChatPhotoView photo) {
        return new ChatMessageBroadcast(messageId, chatRoomId, senderId, content, sentAt, videoPostId, reactions,
                myReaction, replyTo == null ? null : replyTo.withPhoto(photo), messageType, senderName,
                deleted, senderType, mission, this.photo, clientRequestId);
    }

    public static ChatMessageBroadcast from(ChatMessage message) {
        return from(message, null);
    }

    public static ChatMessageBroadcast from(ChatMessage message, ChatMissionReference mission) {
        return new ChatMessageBroadcast(
                message.id(), message.chatRoomId(), message.senderId(), message.displayContent(), message.createdAt(),
                message.referenceSuppressed() ? null : message.videoPostId(), List.of(), null, replyOf(message), message.messageType().name(),
                message.senderName(), message.isAdminDeleted(), message.senderType().name(),
                message.isAdminDeleted() ? null : mission);
    }

    /** 방금 저장된 답장의 인용. 원문은 저장 직전에 검증했으므로 available=true. */
    private static ReplyToView replyOf(ChatMessage message) {
        if (message.referenceSuppressed() || message.replyToMessageId() == null) {
            return null;
        }
        return ReplyToView.of(message.replyToMessageId(), message.replyPreviewSender(),
                message.replyPreviewContent(), true);
    }
}
