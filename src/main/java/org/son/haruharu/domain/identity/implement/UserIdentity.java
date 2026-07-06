package org.son.haruharu.domain.identity.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.son.haruharu.utils.MaskingUtils.mask;

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
    @Override
    public String toString() {
        return "UserIdentity[id=%s, userId=%s, ci=%s, di=%s, phoneNumber=%s, birthday=%s, gender=%s, userName=%s, operator=%s, isForeigner=%s, verifiedAt=%s]"
                .formatted(id, userId, mask(ci), mask(di), mask(phoneNumber), birthday, gender, mask(userName), operator, isForeigner, verifiedAt);
    }
}
