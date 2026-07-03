package org.son.haruharu.domain.identity.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserIdentity(
        Long id,
        Long userId,
        String ci,
        String di,
        String phoneNumber,
        LocalDate birthday,
        String gender,
        String userName,
        String operator,
        boolean isForeigner,
        LocalDateTime verifiedAt
) {
}
