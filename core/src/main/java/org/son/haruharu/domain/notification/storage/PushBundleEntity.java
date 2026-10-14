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
import org.son.haruharu.domain.notification.implement.PushBundle;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

// 푸시 묶음 상태는 덮어쓰는 휘발성 데이터라 전역 soft-delete(BaseEntity) 대상에서 제외한다(#505).
@Entity
@Table(name = "push_bundles",
        uniqueConstraints = @UniqueConstraint(name = "ux_push_bundles_user_key", columnNames = {"user_id", "bundle_key"}))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushBundleEntity {

    // 이름(방 가명)에는 줄바꿈이 들어가지 않는다.
    private static final String NAME_SEPARATOR = "\n";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "bundle_key", nullable = false, length = 64)
    private String bundleKey;

    @Column(nullable = false)
    private LocalDateTime lastAlertedAt;

    private Long lastAlertedRef;

    @Column(columnDefinition = "text")
    private String names;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static PushBundleEntity from(PushBundle bundle) {
        PushBundleEntity entity = new PushBundleEntity();
        entity.userId = bundle.userId();
        entity.bundleKey = bundle.bundleKey();
        return entity.update(bundle);
    }

    PushBundleEntity update(PushBundle bundle) {
        lastAlertedAt = bundle.lastAlertedAt();
        lastAlertedRef = bundle.lastAlertedRef();
        names = bundle.names().isEmpty() ? null : String.join(NAME_SEPARATOR, bundle.names());
        return this;
    }

    public PushBundle toDomain() {
        List<String> list = names == null ? List.of() : Arrays.asList(names.split(NAME_SEPARATOR));
        return new PushBundle(id, userId, bundleKey, lastAlertedAt, lastAlertedRef, list);
    }
}
