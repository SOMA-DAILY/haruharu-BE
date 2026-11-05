package org.son.haruharu.domain.user.storage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
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

    /**
     * 탈퇴 90일 뒤 개인정보 파기를 마친 시각(#555). 파기 스케줄러만 native UPDATE 로 쓰고 JPA 는 읽지도 쓰지도 않는다 —
     * from()/save 가 null 로 덮어써 재파기 대상으로 되돌리지 않도록 insertable/updatable 을 막는다.
     * 컬럼 생성은 scripts/sql/555-users-purged-at-up.sql (prod 태스크는 ddl-auto=update 환경변수라 자동으로도 생긴다).
     */
    @Column(name = "purged_at", insertable = false, updatable = false)
    private java.time.LocalDateTime purgedAt;

    public static UserEntity from(User user) {
        var entity = new UserEntity();
        entity.id = user.id();
        entity.status = user.status();
        entity.createdAt = user.createdAt();
        return entity;
    }

    public User toDomain() {
        return new User(id, status, createdAt);
    }
}
