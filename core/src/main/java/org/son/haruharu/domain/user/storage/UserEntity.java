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
