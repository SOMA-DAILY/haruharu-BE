package org.son.haruharu.infrastructure.pass;

import java.time.LocalDate;

public record PassVerificationResult(
        String ci,
        String di,
        String phoneNumber,
        LocalDate birthday,
        String gender,
        String userName,
        String operator,
        boolean isForeigner
) {}
