package org.son.haruharu.domain.chat.storage;

import jakarta.persistence.Column;
import jakarta.persistence.CheckConstraint;
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
import org.son.haruharu.domain.chat.implement.ChatPhoto;
import org.son.haruharu.domain.chat.implement.ChatMessageType;
import org.son.haruharu.domain.chat.implement.ChatSenderType;

import java.time.LocalDateTime;

@org.hibernate.annotations.SQLRestriction("member_removed_at is null")
@Entity
@Table(name = "chat_messages", uniqueConstraints = {
        @UniqueConstraint(name="uq_chat_messages_reply_request", columnNames={"chat_room_id", "sender_id", "client_request_id"}),
        @UniqueConstraint(name="uq_chat_messages_schedule_item", columnNames="schedule_item_id"),
        @UniqueConstraint(name="uq_chat_messages_mission_announcement", columnNames="mission_announcement_id")
}, indexes = {
        @Index(name = "idx_chat_messages_room_id", columnList = "chat_room_id, id"),
        @Index(name = "idx_chat_messages_reply_to", columnList = "reply_to_message_id"),
        @Index(name = "idx_chat_messages_room_mission", columnList = "room_mission_id")
}, check = {
        @CheckConstraint(name = "ck_chat_messages_photo", constraint =
                "(message_type='IMAGE' AND sender_type='USER' AND client_request_id IS NOT NULL "
                + "AND photo_key IS NOT NULL AND photo_width IS NOT NULL AND photo_width>0 "
                + "AND photo_height IS NOT NULL AND photo_height>0 AND photo_digest IS NOT NULL) "
                + "OR (message_type<>'IMAGE' AND photo_key IS NULL AND photo_width IS NULL AND photo_height IS NULL AND photo_digest IS NULL)"),
        @CheckConstraint(name = "ck_chat_messages_sender",
                constraint = "(sender_type = 'USER' AND sender_id IS NOT NULL) OR (sender_type = 'SYSTEM' AND sender_id IS NULL)"),
        @CheckConstraint(name = "ck_chat_messages_mission_reference",
                constraint = "(message_type IN ('TEXT', 'IMAGE') AND room_mission_id IS NULL AND mission_announcement_id IS NULL) "
                        + "OR (message_type = 'MISSION' AND sender_type = 'SYSTEM' AND room_mission_id IS NOT NULL "
                        + "AND room_mission_id > 0 AND mission_announcement_id IS NOT NULL AND mission_announcement_id > 0)")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageEntity extends BaseEntity {

    /** 관리자 퇴장에 따른 비노출. 원본·일반 삭제 상태는 별도로 보존한다. */
    @Column(name = "member_removed_at", updatable=false)
    private java.time.LocalDateTime memberRemovedAt;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "sender_id")
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private ChatSenderType senderType;

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

    @Column(name = "room_mission_id")
    private Long roomMissionId;

    @Column(name = "mission_announcement_id")
    private Long missionAnnouncementId;

    // 운영자 삭제. 전역 @SoftDelete(deleted_at)는 행을 숨겨 '삭제된 메시지'를 못 내리므로 별도 컬럼.
    @Column(name = "admin_deleted_at")
    private LocalDateTime adminDeletedAt;

    @Column(name = "delete_broadcast_at")
    private LocalDateTime deleteBroadcastAt;

    @Column(name = "photo_key", length = 200)
    private String photoKey;
    @Column(name = "photo_width")
    private Integer photoWidth;
    @Column(name = "photo_height")
    private Integer photoHeight;
    @Column(name = "photo_digest", length = 64)
    private String photoDigest;

    @org.hibernate.annotations.ColumnDefault("false")
    @Column(nullable=false)
    private boolean referenceSuppressed;

    public static ChatMessageEntity from(ChatMessage message) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.id = message.id();
        entity.referenceSuppressed = message.referenceSuppressed();
        entity.chatRoomId = message.chatRoomId();
        entity.senderId = message.senderId();
        entity.senderType = message.senderType();
        entity.content = message.content();
        entity.videoPostId = message.videoPostId();
        entity.clientRequestId = message.clientRequestId();
        entity.replyToMessageId = message.replyToMessageId();
        entity.replyPreviewSender = message.replyPreviewSender();
        entity.replyPreviewContent = message.replyPreviewContent();
        entity.messageType = message.messageType();
        entity.scheduleItemId = message.scheduleItemId();
        entity.roomMissionId = message.roomMissionId();
        entity.missionAnnouncementId = message.missionAnnouncementId();
        entity.adminDeletedAt = message.adminDeletedAt();
        entity.deleteBroadcastAt = message.deleteBroadcastAt();
        entity.createdAt = message.createdAt();
        if (message.photo() != null) {
            entity.photoKey = message.photo().key();
            entity.photoWidth = message.photo().width();
            entity.photoHeight = message.photo().height();
            entity.photoDigest = message.photo().digest();
        }
        return entity;
    }

    public ChatMessage toDomain() {
        return new ChatMessage(id, chatRoomId, senderId, content, createdAt, videoPostId, clientRequestId,
                replyToMessageId, replyPreviewSender, replyPreviewContent, messageType, scheduleItemId,
                adminDeletedAt, deleteBroadcastAt, senderType, roomMissionId, missionAnnouncementId,
                photoKey == null ? null : new ChatPhoto(photoKey, photoWidth, photoHeight, photoDigest), referenceSuppressed);
    }
}
