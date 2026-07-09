package org.son.haruharu.domain.agreement.implement;

import java.util.Optional;

public interface UserAgreementRepository {
    UserAgreement save(UserAgreement agreement);

    Optional<UserAgreement> findLatestByUserIdAndType(Long userId, AgreementType type);
}
