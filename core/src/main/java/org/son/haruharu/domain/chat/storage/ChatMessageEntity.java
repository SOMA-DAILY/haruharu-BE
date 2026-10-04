package org.son.haruharu.domain.chat.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMission;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_messages_room_id", columnList = "chat_room_id, id")
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

    @Column(unique = true) private Long operatorMissionId;
    private Long missionImageId;
    private Long missionRoomId;
    @Column(length = 20) private String missionTemplateId;
    @Column(length = 40) private String missionTitle;
    @Column(nullable = false, columnDefinition = "boolean default false") private boolean adminDeleted;

    public static ChatMessageEntity from(ChatMessage message) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.id = message.id();
        entity.chatRoomId = message.chatRoomId();
        entity.senderId = message.senderId();
        entity.content = message.content();
        entity.createdAt = message.createdAt();
        entity.adminDeleted = message.adminDeleted();
        if (message.mission() != null) {
            entity.operatorMissionId = message.mission().missionId();
            entity.missionRoomId = message.mission().roomId();
            entity.missionImageId = message.mission().imageId();
            entity.missionTemplateId = message.mission().templateId();
            entity.missionTitle = message.mission().title();
        }
        return entity;
    }

    public ChatMessage toDomain() {
        ChatMission mission = operatorMissionId == null ? null
                : new ChatMission(operatorMissionId, missionRoomId, missionTemplateId, missionTitle, missionImageId, null);
        return new ChatMessage(id, chatRoomId, senderId, content, createdAt, mission, adminDeleted);
    }
}
