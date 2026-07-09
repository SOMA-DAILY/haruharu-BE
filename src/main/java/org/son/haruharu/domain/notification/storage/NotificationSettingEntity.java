package org.son.haruharu.domain.notification.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.notification.implement.NotificationSetting;

@Entity
@Table(name = "notification_settings",
        uniqueConstraints = @UniqueConstraint(name = "ux_notification_settings_user_id", columnNames = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSettingEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private boolean activityEnabled;

    @Column(nullable = false)
    private boolean marketingEnabled;

    public static NotificationSettingEntity from(NotificationSetting setting) {
        NotificationSettingEntity entity = new NotificationSettingEntity();
        entity.id = setting.id();
        entity.userId = setting.userId();
        entity.activityEnabled = setting.activityEnabled();
        entity.marketingEnabled = setting.marketingEnabled();
        return entity;
    }

    public NotificationSetting toDomain() {
        return new NotificationSetting(id, userId, activityEnabled, marketingEnabled);
    }
}
