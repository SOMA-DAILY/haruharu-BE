package org.son.haruharu.domain.mission.chat.storage;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncement;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementStatus;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType;

@Entity
@Table(name = "mission_announcements", uniqueConstraints = {
        @UniqueConstraint(name = "uq_mission_announcements_legacy", columnNames = "legacy_schedule_item_id"),
        @UniqueConstraint(name = "uq_mission_announcements_id_mission", columnNames = {"id", "room_mission_id"})
}, indexes = {
        @Index(name = "idx_mission_announcements_due", columnList = "status, run_at"),
        @Index(name = "idx_mission_announcements_room", columnList = "room_id, run_at"),
        @Index(name = "idx_mission_announcements_mission", columnList = "room_mission_id, run_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionAnnouncementEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_mission_id", nullable = false)
    private Long roomMissionId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "announcement_type", nullable = false, length = 20)
    private MissionAnnouncementType announcementType;

    @Column(name = "run_at", nullable = false)
    private LocalDateTime runAt;

    @Column(name = "content", length = 500)
    private String content;

    @Column(name = "push", nullable = false)
    private boolean push;

    @Column(name = "push_title", length = 15)
    private String pushTitle;

    @Column(name = "push_body", length = 30)
    private String pushBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MissionAnnouncementStatus status;

    @Column(name = "fail_reason", length = 200)
    private String failReason;

    @Column(name = "sent_message_id")
    private Long sentMessageId;

    @Column(name = "legacy_schedule_item_id")
    private Long legacyScheduleItemId;

    @Column(name = "created_by", length = 40)
    private String createdBy;

    @Column(name = "updated_by", length = 40)
    private String updatedBy;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public static MissionAnnouncementEntity from(MissionAnnouncement value) {
        MissionAnnouncementEntity entity = new MissionAnnouncementEntity();
        entity.id = value.id();
        entity.roomMissionId = value.roomMissionId();
        entity.roomId = value.roomId();
        entity.announcementType = value.announcementType();
        entity.runAt = value.runAt();
        entity.content = value.content();
        entity.push = value.push();
        entity.pushTitle = value.pushTitle();
        entity.pushBody = value.pushBody();
        entity.status = value.status();
        entity.failReason = value.failReason();
        entity.sentMessageId = value.sentMessageId();
        entity.legacyScheduleItemId = value.legacyScheduleItemId();
        entity.createdBy = value.createdBy();
        entity.updatedBy = value.updatedBy();
        entity.sentAt = value.sentAt();
        entity.createdAt = value.createdAt();
        entity.updatedAt = value.updatedAt();
        return entity;
    }

    public MissionAnnouncement toDomain() {
        return new MissionAnnouncement(id, roomMissionId, roomId, announcementType, runAt, content, push, pushTitle, pushBody, status,
                failReason, sentMessageId, legacyScheduleItemId, createdBy, updatedBy, sentAt, createdAt, updatedAt);
    }
}
