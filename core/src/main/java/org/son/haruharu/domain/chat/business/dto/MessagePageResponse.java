package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMissionReference;

import java.time.LocalDateTime;
import java.util.List;

/** 메시지 목록(최신순 커서 페이지). nextCursor는 다음 페이지 조회에 넘길 마지막 메시지 id. */
public record MessagePageResponse(
        @Schema(description = "메시지 목록(최신순)") List<MessageItem> messages,
        @Schema(description = "다음 페이지 조회에 넘길 커서(마지막 메시지 id). 다음 페이지 없으면 null", example = "995") Long nextCursor
) {
    /** SYSTEM은 sender_id=null. content는 운영자가 지웠으면 '삭제된 메시지'. */
    public record MessageItem(
            @Schema(description = "메시지 id", example = "1024") Long messageId,
            @Schema(description = "USER의 유저 id. SYSTEM이면 null", example = "102", nullable = true) Long senderId,
            @Schema(description = "메시지 내용", example = "내일 만나요!") String content,
            @Schema(description = "전송 시각") LocalDateTime sentAt,
            Long videoPostId,
            @Schema(description = "반응 타입별 개수(많은 순)") List<ReactionCountItem> reactions,
            @Schema(description = "내 반응(없으면 null)", example = "LAUGH", nullable = true) String myReaction,
            @Schema(description = "답장 인용(답장 아니면 null)", nullable = true) ReplyToView replyTo,
            @Schema(description = "내용 유형(TEXT 또는 MISSION)", example = "TEXT") String messageType,
            @Schema(description = "운영 글이면 '하루', 아니면 null(앱이 members 에서 찾음)", nullable = true) String senderName,
            @Schema(description = "운영자가 지운 메시지면 true(content 는 '삭제된 메시지')") boolean deleted,
            @Schema(description = "발신자 유형(USER 또는 SYSTEM)", example = "SYSTEM") String senderType,
            @Schema(description = "미션 참조. 일반·삭제된 메시지에는 null", nullable = true) ChatMissionReference mission,
            ChatPhotoView photo,
            String clientRequestId
    ) {
        public MessageItem(Long messageId, Long senderId, String content, LocalDateTime sentAt, Long videoPostId, List<ReactionCountItem> reactions, String myReaction, ReplyToView replyTo, String messageType, String senderName, boolean deleted, String senderType, ChatMissionReference mission) {
            this(messageId, senderId, content, sentAt, videoPostId, reactions, myReaction, replyTo, messageType, senderName, deleted, senderType, mission, null, null);
        }

        public MessageItem(Long messageId, Long senderId, String content, LocalDateTime sentAt) {
            this(messageId, senderId, content, sentAt, null, List.of(), null, null, "TEXT", null, false, "USER", null);
        }

        public MessageItem withPhoto(ChatPhotoView photo, String requestId) {
            return new MessageItem(messageId, senderId, content, sentAt, videoPostId, reactions, myReaction, replyTo, messageType, senderName, deleted, senderType, mission, deleted ? null : photo, requestId);
        }

        public static MessageItem of(ChatMessage message, List<ReactionCountItem> reactions, String myReaction,
                                     ReplyToView replyTo) {
            return of(message, reactions, myReaction, replyTo, null);
        }

        public static MessageItem of(ChatMessage message, List<ReactionCountItem> reactions, String myReaction,
                                     ReplyToView replyTo, ChatMissionReference mission) {
            return new MessageItem(message.id(), message.senderId(), message.displayContent(), message.createdAt(),
                    message.referenceSuppressed() ? null : message.videoPostId(), reactions, myReaction, replyTo, message.messageType().name(),
                    message.senderName(), message.isAdminDeleted(), message.senderType().name(),
                    message.isAdminDeleted() ? null : mission);
        }
    }

    /** 항목은 호출자가 만든다(반응·답장 배치 조회 결과를 채운 채로). messages 와 items 는 같은 순서. */
    public static MessagePageResponse of(List<ChatMessage> messages, int size, List<MessageItem> items) {
        boolean hasMore = messages.size() >= size;
        Long nextCursor = hasMore ? messages.get(messages.size() - 1).id() : null;
        return new MessagePageResponse(items, nextCursor);
    }
}
