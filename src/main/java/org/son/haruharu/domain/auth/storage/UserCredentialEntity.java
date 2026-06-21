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

import java.util.UUID;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider_type", nullable = false, length = 20)
    private String providerType;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    public static UserCredentialEntity from(UserCredential userCredential) {
        throw new UnsupportedOperationException("TODO");
    }

    public UserCredential toDomain() {
        throw new UnsupportedOperationException("TODO");
    }
}
