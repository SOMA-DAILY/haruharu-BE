package org.son.haruharu.domain.chat.business.dto;

import org.son.haruharu.domain.chat.implement.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;

/** 메시지 목록(최신순 커서 페이지). nextCursor는 다음 페이지 조회에 넘길 마지막 메시지 id. */
public record MessagePageResponse(
        List<MessageItem> messages,
        Long nextCursor
) {
    public record MessageItem(
            Long messageId,
            Long senderId,
            String content,
            LocalDateTime sentAt
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
