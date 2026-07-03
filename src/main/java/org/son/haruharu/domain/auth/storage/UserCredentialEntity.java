package org.son.haruharu.domain.auth.storage;

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
import org.son.haruharu.domain.auth.implement.UserCredential;

@Entity
@Table(
        name = "user_credentials",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_credentials_provider",
                columnNames = {"provider_type", "provider_id"}
        )
)
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
