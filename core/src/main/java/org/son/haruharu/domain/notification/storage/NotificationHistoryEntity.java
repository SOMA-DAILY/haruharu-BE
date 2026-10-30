package org.son.haruharu.domain.notification.storage;

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
import org.son.haruharu.domain.notification.implement.NotificationHistory;
import org.son.haruharu.domain.notification.implement.NotificationType;

import java.util.Map;

/** payload는 JSON 문자열로 저장한다(Map ↔ JSON 변환은 RepositoryImpl 담당). */
@org.hibernate.annotations.SQLRestriction("member_removed_at is null")
@Entity
@Table(name = "notification_history",
        indexes = @Index(name = "ix_nh_user_id", columnList = "userId, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationHistoryEntity extends BaseEntity {

    /** 관리자 퇴장에 따른 비노출. 원본·일반 삭제 상태는 별도로 보존한다. */
    @Column(name = "member_removed_at", updatable=false)
    private java.time.LocalDateTime memberRemovedAt;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 200)
    private String body;

    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false)
    private boolean isRead;

    public static NotificationHistoryEntity from(NotificationHistory history, String payloadJson) {
        NotificationHistoryEntity entity = new NotificationHistoryEntity();
        entity.id = history.id();
        entity.userId = history.userId();
        entity.type = history.type();
        entity.title = history.title();
        entity.body = history.body();
        entity.payloadJson = payloadJson;
        entity.isRead = history.isRead();
        entity.createdAt = history.createdAt();
        return entity;
    }

    public NotificationHistory toDomain(Map<String, String> payload) {
        return new NotificationHistory(id, userId, type, title, body, payload, isRead, createdAt);
    }

    public String payloadJson() {
        return payloadJson;
    }
}
