package org.son.haruharu.domain.user.storage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    // 앱스토어 심사용 데모 계정 여부(#264). 데모/내부 계정만 true — /demo API 게이트와 /users/me 노출의 기준.
    // 기존 행 마이그레이션 안전을 위해 @ColumnDefault("false").
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean demoMode;

    public static UserEntity from(User user) {
        var entity = new UserEntity();
        entity.id = user.id();
        entity.status = user.status();
        entity.demoMode = user.demoMode();
        return entity;
    }

    public User toDomain() {
        return new User(id, status, createdAt, demoMode);
    }
}
