package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.chat.implement.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;

/** 메시지 목록(최신순 커서 페이지). nextCursor는 다음 페이지 조회에 넘길 마지막 메시지 id. */
public record MessagePageResponse(
        @Schema(description = "메시지 목록(최신순)") List<MessageItem> messages,
        @Schema(description = "다음 페이지 조회에 넘길 커서(마지막 메시지 id). 다음 페이지 없으면 null", example = "995") Long nextCursor
) {
    /**
     * 필드는 추가만 한다(#456: reactions, my_reaction, reply_to / #457: message_type, sender_name, deleted).
     * sender_id 는 운영 글에서도 시스템 유저 id(양수)로 채우고, content 는 운영자가 지웠으면 '삭제된 메시지'.
     */
    public record MessageItem(
            @Schema(description = "메시지 id", example = "1024") Long messageId,
            @Schema(description = "보낸 사람 유저 id", example = "102") Long senderId,
            @Schema(description = "메시지 내용", example = "내일 만나요!") String content,
            @Schema(description = "전송 시각") LocalDateTime sentAt,
            Long videoPostId,
            @Schema(description = "반응 타입별 개수(많은 순)") List<ReactionCountItem> reactions,
            @Schema(description = "내 반응(없으면 null)", example = "LAUGH", nullable = true) String myReaction,
            @Schema(description = "답장 인용(답장 아니면 null)", nullable = true) ReplyToView replyTo,
            @Schema(description = "TEXT 또는 ADMIN(운영 글)", example = "TEXT") String messageType,
            @Schema(description = "운영 글이면 '하루', 아니면 null(앱이 members 에서 찾음)", nullable = true) String senderName,
            @Schema(description = "운영자가 지운 메시지면 true(content 는 '삭제된 메시지')") boolean deleted
    ) {
        public MessageItem(Long messageId, Long senderId, String content, LocalDateTime sentAt) {
            this(messageId, senderId, content, sentAt, null, List.of(), null, null, "TEXT", null, false);
        }

        public static MessageItem of(ChatMessage message, List<ReactionCountItem> reactions, String myReaction,
                                     ReplyToView replyTo) {
            return new MessageItem(message.id(), message.senderId(), message.displayContent(), message.createdAt(),
                    message.videoPostId(), reactions, myReaction, replyTo, message.messageType().name(),
                    message.senderName(), message.isAdminDeleted());
        }
    }

    /** 항목은 호출자가 만든다(반응·답장 배치 조회 결과를 채운 채로). messages 와 items 는 같은 순서. */
    public static MessagePageResponse of(List<ChatMessage> messages, int size, List<MessageItem> items) {
        boolean hasMore = messages.size() >= size;
        Long nextCursor = hasMore ? messages.get(messages.size() - 1).id() : null;
        return new MessagePageResponse(items, nextCursor);
    }
}
