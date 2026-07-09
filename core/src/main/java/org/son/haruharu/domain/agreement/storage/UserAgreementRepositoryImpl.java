package org.son.haruharu.domain.agreement.storage;

import org.son.haruharu.domain.agreement.implement.AgreementType;
import org.son.haruharu.domain.agreement.implement.UserAgreement;
import org.son.haruharu.domain.agreement.implement.UserAgreementRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserAgreementRepositoryImpl implements UserAgreementRepository {

    private final UserAgreementJpaRepository jpaRepository;

    public UserAgreementRepositoryImpl(UserAgreementJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public UserAgreement save(UserAgreement agreement) {
        return jpaRepository.save(UserAgreementEntity.from(agreement)).toDomain();
    }

    @Override
    public Optional<UserAgreement> findLatestByUserIdAndType(Long userId, AgreementType type) {
        return jpaRepository.findTopByUserIdAndTypeOrderByIdDesc(userId, type).map(UserAgreementEntity::toDomain);
    }
}
