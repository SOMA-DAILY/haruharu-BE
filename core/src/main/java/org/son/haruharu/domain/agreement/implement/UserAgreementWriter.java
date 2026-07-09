package org.son.haruharu.domain.agreement.implement;

import org.springframework.stereotype.Component;

@Component
public class UserAgreementWriter {

    private final UserAgreementRepository userAgreementRepository;

    public UserAgreementWriter(UserAgreementRepository userAgreementRepository) {
        this.userAgreementRepository = userAgreementRepository;
    }

    public UserAgreement save(UserAgreement agreement) {
        return userAgreementRepository.save(agreement);
    }
}
