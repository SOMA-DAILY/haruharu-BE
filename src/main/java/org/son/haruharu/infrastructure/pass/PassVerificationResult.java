package org.son.haruharu.infrastructure.pass;

import java.time.LocalDate;

import static org.son.haruharu.utils.MaskingUtils.mask;

public record PassVerificationResult(
        String ci,
        String di,
        String phoneNumber,
        LocalDate birthday,
        String gender,
        String userName,
        String operator,
        boolean isForeigner
) {
    @Override
    public String toString() {
        return "PassVerificationResult[ci=%s, di=%s, phoneNumber=%s, birthday=%s, gender=%s, userName=%s, operator=%s, isForeigner=%s]"
                .formatted(mask(ci), mask(di), mask(phoneNumber), birthday, gender, mask(userName), operator, isForeigner);
    }
}
