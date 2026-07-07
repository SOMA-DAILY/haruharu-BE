package org.son.haruharu.domain.agreement.storage;

import org.son.haruharu.domain.agreement.implement.AgreementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAgreementJpaRepository extends JpaRepository<UserAgreementEntity, Long> {
    Optional<UserAgreementEntity> findTopByUserIdAndTypeOrderByIdDesc(Long userId, AgreementType type);
}
