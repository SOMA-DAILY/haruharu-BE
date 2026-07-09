package org.son.haruharu.domain.agreement.implement;

import java.time.LocalDateTime;

public record UserAgreement(
        Long id,
        Long userId,
        AgreementType type,
        String version,
        boolean agreed,
        LocalDateTime agreedAt
) {}
