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
    public record MessageItem(
            @Schema(description = "메시지 id", example = "1024") Long messageId,
            @Schema(description = "보낸 사람 유저 id", example = "102") Long senderId,
            @Schema(description = "메시지 내용", example = "내일 만나요!") String content,
            @Schema(description = "전송 시각") LocalDateTime sentAt
    ) {
        public static MessageItem from(ChatMessage message) {
            return new MessageItem(message.id(), message.senderId(), message.content(), message.createdAt());
        }
    }

    public static MessagePageResponse of(List<ChatMessage> messages, int size) {
        boolean hasMore = messages.size() >= size;
        Long nextCursor = hasMore ? messages.get(messages.size() - 1).id() : null;
        List<MessageItem> items = messages.stream().map(MessageItem::from).toList();
        return new MessagePageResponse(items, nextCursor);
    }
}
