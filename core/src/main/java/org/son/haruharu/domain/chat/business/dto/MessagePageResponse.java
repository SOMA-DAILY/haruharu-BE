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
    /** 필드는 추가만 한다(#456: reactions, my_reaction, reply_to). */
    public record MessageItem(
            @Schema(description = "메시지 id", example = "1024") Long messageId,
            @Schema(description = "보낸 사람 유저 id", example = "102") Long senderId,
            @Schema(description = "메시지 내용", example = "내일 만나요!") String content,
            @Schema(description = "전송 시각") LocalDateTime sentAt,
            Long videoPostId,
            @Schema(description = "반응 타입별 개수(많은 순)") List<ReactionCountItem> reactions,
            @Schema(description = "내 반응(없으면 null)", example = "LAUGH", nullable = true) String myReaction,
            @Schema(description = "답장 인용(답장 아니면 null)", nullable = true) ReplyToView replyTo
    ) {
        public MessageItem(Long messageId, Long senderId, String content, LocalDateTime sentAt) {
            this(messageId, senderId, content, sentAt, null);
        }

        public MessageItem(Long messageId, Long senderId, String content, LocalDateTime sentAt, Long videoPostId) {
            this(messageId, senderId, content, sentAt, videoPostId, List.of(), null, null);
        }

        public static MessageItem from(ChatMessage message) {
            return new MessageItem(message.id(), message.senderId(), message.content(), message.createdAt(), message.videoPostId());
        }
    }

    public static MessagePageResponse of(List<ChatMessage> messages, int size) {
        return of(messages, size, messages.stream().map(MessageItem::from).toList());
    }

    /** 항목은 호출자가 만든다(반응·답장 배치 조회 결과를 채운 채로). messages 와 items 는 같은 순서. */
    public static MessagePageResponse of(List<ChatMessage> messages, int size, List<MessageItem> items) {
        boolean hasMore = messages.size() >= size;
        Long nextCursor = hasMore ? messages.get(messages.size() - 1).id() : null;
        return new MessagePageResponse(items, nextCursor);
    }
}
