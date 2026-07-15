package org.son.haruharu.domain.identity.storage;

import org.son.haruharu.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

@Entity
@Table(name = "user_identities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserIdentityEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Convert(converter = CiDiCryptoConverter.class)
    @Column(nullable = false)
    private String ci;

    /**
     * blind index. 유니크는 partial index(uq_user_identities_ci_hash_active, WHERE deleted_at IS NULL)로
     * 관리한다 — JPA는 partial을 표현할 수 없어 unique=true를 두면 soft-delete된 탈퇴 행이 슬롯을 점유해
     * 재인증 INSERT가 충돌한다(#245, user_credentials의 #147과 동일 패턴).
     */
    @Column(nullable = false)
    private String ciHash;

    @Convert(converter = CiDiCryptoConverter.class)
    @Column(nullable = false)
    private String di;

    // 통신사 사전승인을 받은 채널에서만 제공된다(다날 미승인 시 null).
    @Column(length = 20)
    private String phoneNumber;

    @Column(nullable = false)
    private LocalDate birthday;

    @Column(nullable = false, length = 10)
    private String gender;

    @Column(nullable = false, length = 100)
    private String userName;

    // 통신사 사전승인을 받은 채널에서만 제공된다(다날 미승인 시 null).
    @Column(length = 10)
    private String operator;

    @Column(nullable = false)
    private boolean isForeigner;

    @Column(nullable = false)
    private LocalDateTime verifiedAt;

    public static UserIdentityEntity from(UserIdentity userIdentity, String ciHash) {
        UserIdentityEntity entity = new UserIdentityEntity();
        entity.id = userIdentity.id();
        entity.userId = userIdentity.userId();
        entity.ci = userIdentity.ci();
        entity.ciHash = ciHash;
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
