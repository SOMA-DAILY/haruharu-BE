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
import org.son.haruharu.domain.notification.implement.DeviceToken;

@Entity
@Table(name = "device_tokens",
        uniqueConstraints = @UniqueConstraint(name = "ux_device_tokens_token", columnNames = "token"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceTokenEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 512)
    private String token;

    public static DeviceTokenEntity from(DeviceToken deviceToken) {
        DeviceTokenEntity entity = new DeviceTokenEntity();
        entity.id = deviceToken.id();
        entity.userId = deviceToken.userId();
        entity.token = deviceToken.token();
        return entity;
    }

    public DeviceToken toDomain() {
        return new DeviceToken(id, userId, token);
    }
}
