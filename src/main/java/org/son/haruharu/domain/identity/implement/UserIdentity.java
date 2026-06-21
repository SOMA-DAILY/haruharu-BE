package org.son.haruharu.domain.identity.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserIdentity(
        UUID id,
        UUID userId,
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
