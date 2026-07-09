package org.son.haruharu.domain.notification.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.notification.implement.DeviceToken;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// FCM 토큰은 휴발성 인프라 데이터라 전역 soft-delete(BaseEntity) 대상에서 제외한다.
// 재등록(unregister→register) 시 unique(token) 충돌을 피하려면 물리삭제가 필요하다.
@Entity
@Table(name = "device_tokens",
        uniqueConstraints = @UniqueConstraint(name = "ux_device_tokens_token", columnNames = "token"))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 512)
    private String token;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

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
