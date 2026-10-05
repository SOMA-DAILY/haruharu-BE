package org.son.haruharu.domain.chat.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.son.haruharu.domain.chat.implement.ChatMessageType;
import org.son.haruharu.domain.chat.implement.ChatMission;

import java.time.LocalDateTime;

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

    // 운영 글(#457). 기존 행은 컬럼 default 'TEXT'.
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private ChatMessageType messageType;

    @Column(name = "schedule_item_id")
    private Long scheduleItemId;

    // 운영자 삭제. 전역 @SoftDelete(deleted_at)는 행을 숨겨 '삭제된 메시지'를 못 내리므로 별도 컬럼.
    @Column(name = "admin_deleted_at")
    private LocalDateTime adminDeletedAt;

    @Column(name = "delete_broadcast_at")
    private LocalDateTime deleteBroadcastAt;

    // 운영자 미션 카드(#474). 미션 1건당 카드 1개(unique). 삭제 표시는 위 admin_deleted_at 을 같이 쓴다.
    @Column(name = "operator_mission_id", unique = true)
    private Long operatorMissionId;

    @Column(name = "mission_image_id")
    private Long missionImageId;

    @Column(name = "mission_room_id")
    private Long missionRoomId;

    @Column(name = "mission_template_id", length = 20)
    private String missionTemplateId;

    @Column(name = "mission_title", length = 40)
    private String missionTitle;

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
        entity.messageType = message.messageType();
        entity.scheduleItemId = message.scheduleItemId();
        entity.adminDeletedAt = message.adminDeletedAt();
        entity.deleteBroadcastAt = message.deleteBroadcastAt();
        entity.createdAt = message.createdAt();
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
        return new ChatMessage(id, chatRoomId, senderId, content, createdAt, videoPostId, clientRequestId,
                replyToMessageId, replyPreviewSender, replyPreviewContent, messageType, scheduleItemId,
                adminDeletedAt, deleteBroadcastAt, mission);
    }
}
