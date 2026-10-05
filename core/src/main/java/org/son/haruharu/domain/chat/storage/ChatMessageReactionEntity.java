package org.son.haruharu.domain.chat.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatMessageReaction;

import java.time.LocalDateTime;

/** video_reactions 와 같은 구조(#456). BaseEntity 를 쓰지 않아 해제는 물리 삭제다(UNIQUE 재사용). */
@Entity
@Table(name = "chat_message_reactions",
        uniqueConstraints = @UniqueConstraint(name = "uq_chat_message_reactions_message_user",
                columnNames = {"message_id", "user_id"}),
        indexes = @Index(name = "idx_chat_message_reactions_message", columnList = "message_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageReactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reaction_type", nullable = false, length = 20)
    private String reactionType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ChatMessageReactionEntity from(ChatMessageReaction reaction) {
        ChatMessageReactionEntity entity = new ChatMessageReactionEntity();
        entity.id = reaction.id();
        entity.messageId = reaction.messageId();
        entity.userId = reaction.userId();
        entity.reactionType = reaction.reactionType();
        entity.createdAt = reaction.createdAt();
        return entity;
    }

    public ChatMessageReaction toDomain() {
        return new ChatMessageReaction(id, messageId, userId, reactionType, createdAt);
    }
}
