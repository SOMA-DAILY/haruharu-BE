package org.son.haruharu.domain.roomschedule.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.roomschedule.implement.RevealField;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItem;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "room_schedule_items", indexes = {
        @Index(name = "idx_room_schedule_items_status_run_at", columnList = "status, run_at"),
        @Index(name = "idx_room_schedule_items_room_run_at", columnList = "room_id, run_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomScheduleItemEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "run_at", nullable = false)
    private LocalDateTime runAt;

    // 쉼표 목록(MBTI,INTO_NOW). 항목 값 검증은 코드(ScheduleContent)가 한다.
    @Column(name = "reveal_fields", length = 60)
    private String revealFields;

    @Column(name = "message_content", length = 500)
    private String messageContent;

    @Column(nullable = false)
    private boolean push;

    @Column(name = "push_title", length = 15)
    private String pushTitle;

    @Column(name = "push_body", length = 30)
    private String pushBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomScheduleStatus status;

    @Column(name = "fail_reason", length = 200)
    private String failReason;

    @Column(name = "sent_message_id")
    private Long sentMessageId;

    @Column(name = "created_by", length = 40)
    private String createdBy;

    @Column(name = "updated_by", length = 40)
    private String updatedBy;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public static RoomScheduleItemEntity from(RoomScheduleItem item) {
        RoomScheduleItemEntity entity = new RoomScheduleItemEntity();
        entity.id = item.id();
        entity.roomId = item.roomId();
        entity.runAt = item.runAt();
        entity.revealFields = RevealField.join(item.revealFields());
        entity.messageContent = item.messageContent();
        entity.push = item.push();
        entity.pushTitle = item.pushTitle();
        entity.pushBody = item.pushBody();
        entity.status = item.status();
        entity.failReason = item.failReason();
        entity.sentMessageId = item.sentMessageId();
        entity.createdBy = item.createdBy();
        entity.updatedBy = item.updatedBy();
        entity.sentAt = item.sentAt();
        entity.createdAt = item.createdAt();
        return entity;
    }

    public RoomScheduleItem toDomain() {
        return new RoomScheduleItem(id, roomId, runAt, RevealField.parseCsv(revealFields), messageContent, push,
                pushTitle, pushBody, status, failReason, sentMessageId, createdBy, updatedBy, sentAt, createdAt,
                updatedAt);
    }
}
