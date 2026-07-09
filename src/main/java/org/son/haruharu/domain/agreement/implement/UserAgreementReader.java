package org.son.haruharu.domain.agreement.implement;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserAgreementReader {

    private final UserAgreementRepository userAgreementRepository;

    public UserAgreementReader(UserAgreementRepository userAgreementRepository) {
        this.userAgreementRepository = userAgreementRepository;
    }

    public Optional<UserAgreement> findLatestByType(Long userId, AgreementType type) {
        return userAgreementRepository.findLatestByUserIdAndType(userId, type);
    }
}
