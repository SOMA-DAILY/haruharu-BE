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
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.chat.implement.ChatMessage;

@Entity
@Table(name = "chat_messages", uniqueConstraints = @UniqueConstraint(name="uq_chat_messages_reply_request", columnNames={"chat_room_id", "sender_id", "client_request_id"}), indexes = {
        @Index(name = "idx_chat_messages_room_id", columnList = "chat_room_id, id"),
        @Index(name = "idx_chat_messages_reply_to", columnList = "reply_to_message_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "video_post_id")
    private Long videoPostId;

    @Column(name = "client_request_id", length = 36)
    private String clientRequestId;

    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;

    @Column(name = "reply_preview_sender", length = 20)
    private String replyPreviewSender;

    @Column(name = "reply_preview_content", length = 50)
    private String replyPreviewContent;

    public static ChatMessageEntity from(ChatMessage message) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.id = message.id();
        entity.chatRoomId = message.chatRoomId();
        entity.senderId = message.senderId();
        entity.content = message.content();
        entity.videoPostId = message.videoPostId();
        entity.clientRequestId = message.clientRequestId();
        entity.replyToMessageId = message.replyToMessageId();
        entity.replyPreviewSender = message.replyPreviewSender();
        entity.replyPreviewContent = message.replyPreviewContent();
        entity.createdAt = message.createdAt();
        return entity;
    }

    public ChatMessage toDomain() {
        return new ChatMessage(id, chatRoomId, senderId, content, createdAt, videoPostId, clientRequestId,
                replyToMessageId, replyPreviewSender, replyPreviewContent);
    }
}
