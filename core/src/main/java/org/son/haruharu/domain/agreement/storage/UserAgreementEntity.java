package org.son.haruharu.domain.agreement.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.agreement.implement.AgreementType;
import org.son.haruharu.domain.agreement.implement.UserAgreement;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_agreements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAgreementEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", nullable = false, length = 30)
    private AgreementType type;

    @Column(nullable = false, length = 30)
    private String version;

    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    public static UserAgreementEntity from(UserAgreement agreement) {
        var entity = new UserAgreementEntity();
        entity.id = agreement.id();
        entity.userId = agreement.userId();
        entity.type = agreement.type();
        entity.version = agreement.version();
        entity.agreed = agreement.agreed();
        entity.agreedAt = agreement.agreedAt();
        return entity;
    }

    public UserAgreement toDomain() {
        return new UserAgreement(id, userId, type, version, agreed, agreedAt);
    }
}
