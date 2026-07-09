package org.son.haruharu.domain.auth.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.auth.implement.UserCredential;

/**
 * (provider_type, provider_id) 유니크는 활성 행 한정 partial unique index(WHERE deleted_at IS NULL)로
 * 관리한다. full unique는 soft-delete(@SoftDelete)와 충돌(죽은 행이 슬롯 점유 → 재가입 INSERT 409)하고,
 * partial index는 JPA/H2 DDL로 생성 불가라 @UniqueConstraint를 두지 않는다(#147, 수동 DDL로 반영).
 */
@Entity
@Table(name = "user_credentials")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCredentialEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "provider_type", nullable = false, length = 20)
    private String providerType;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    public static UserCredentialEntity from(UserCredential userCredential) {
        UserCredentialEntity entity = new UserCredentialEntity();
        entity.userId = userCredential.userId();
        entity.providerType = userCredential.providerType();
        entity.providerId = userCredential.providerId();
        return entity;
    }

    public UserCredential toDomain() {
        return new UserCredential(id, userId, providerType, providerId);
    }
}
