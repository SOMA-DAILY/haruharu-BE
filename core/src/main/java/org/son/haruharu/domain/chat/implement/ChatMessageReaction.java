package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;

/** 채팅 메시지 반응(#456). 한 사람당 메시지 1행(UNIQUE). reactionType 은 ReactionType.name(). */
public record ChatMessageReaction(
        Long id,
        Long messageId,
        Long userId,
        String reactionType,
        LocalDateTime createdAt
) {
    public static ChatMessageReaction create(Long messageId, Long userId, String reactionType, LocalDateTime createdAt) {
        return new ChatMessageReaction(null, messageId, userId, reactionType, createdAt);
    }

    public ChatMessageReaction withType(String newType) {
        return new ChatMessageReaction(id, messageId, userId, newType, createdAt);
    }
}
