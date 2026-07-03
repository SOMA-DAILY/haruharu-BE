package org.son.haruharu.domain.identity.storage;

import org.son.haruharu.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.identity.implement.UserIdentity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_identities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserIdentityEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String ci;

    @Column(nullable = false)
    private String di;

    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @Column(nullable = false)
    private LocalDate birthday;

    @Column(nullable = false, length = 10)
    private String gender;

    @Column(nullable = false, length = 100)
    private String userName;

    @Column(nullable = false, length = 10)
    private String operator;

    @Column(nullable = false)
    private boolean isForeigner;

    @Column(nullable = false)
    private LocalDateTime verifiedAt;

    public static UserIdentityEntity from(UserIdentity userIdentity) {
        UserIdentityEntity entity = new UserIdentityEntity();
        entity.id = userIdentity.id();
        entity.userId = userIdentity.userId();
        entity.ci = userIdentity.ci();
        entity.di = userIdentity.di();
        entity.phoneNumber = userIdentity.phoneNumber();
        entity.birthday = userIdentity.birthday();
        entity.gender = userIdentity.gender();
        entity.userName = userIdentity.userName();
        entity.operator = userIdentity.operator();
        entity.isForeigner = userIdentity.isForeigner();
        entity.verifiedAt = userIdentity.verifiedAt();
        return entity;
    }

    public UserIdentity toDomain() {
        return new UserIdentity(id, userId, ci, di, phoneNumber, birthday, gender, userName, operator, isForeigner, verifiedAt);
    }
}
