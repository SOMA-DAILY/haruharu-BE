package org.son.haruharu.domain.meta.business;

import org.son.haruharu.common.enums.LabeledEnum;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;

/**
 * 메타 API가 노출하는 선택지형 enum 레지스트리.
 * key는 응답 JSON의 필드명(camelCase). 신규 선택지 enum 추가 시 상수 1줄만 추가한다.
 */
public enum EnumCatalog {

    DRINKING_STATUS("drinkingStatus", DrinkingStatus.values()),
    SMOKING_STATUS("smokingStatus", SmokingStatus.values());

    private final String key;
    private final LabeledEnum[] values;

    EnumCatalog(String key, LabeledEnum[] values) {
        this.key = key;
        this.values = values;
    }

    public String key() {
        return key;
    }

    public LabeledEnum[] options() {
        return values;
    }
}
