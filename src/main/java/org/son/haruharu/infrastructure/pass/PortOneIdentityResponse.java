package org.son.haruharu.infrastructure.pass;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static org.son.haruharu.utils.MaskingUtils.mask;

/**
 * 포트원 V2 본인인증 조회 응답. 포트원은 camelCase 키를 쓰므로
 * 전역 SNAKE_CASE 전략을 무력화하기 위해 record별로 LowerCamelCase 전략을 명시한다.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record PortOneIdentityResponse(String status, VerifiedCustomer verifiedCustomer) {

    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record VerifiedCustomer(
            String ci,
            String di,
            String name,
            String birthDate,
            String gender,
            String phoneNumber,
            String operator,
            // 다날 등 일부 채널은 이 키를 응답에 담지 않는다. primitive면 null 매핑 시 역직렬화가 실패하므로 래퍼로 둔다.
            Boolean isForeigner
    ) {
        @Override
        public String toString() {
            return "VerifiedCustomer[ci=%s, di=%s, name=%s, birthDate=%s, gender=%s, phoneNumber=%s, operator=%s, isForeigner=%s]"
                    .formatted(mask(ci), mask(di), mask(name), birthDate, gender, mask(phoneNumber), operator, isForeigner);
        }
    }
}
