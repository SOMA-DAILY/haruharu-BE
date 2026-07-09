package org.son.haruharu.domain.meta.business.dto;

import org.son.haruharu.common.enums.LabeledEnum;

/** 프론트 드롭다운 항목 하나. code=서버 저장/전달용 값, label=사용자 노출 한글 라벨. */
public record EnumOptionResponse(String code, String label) {

    public static EnumOptionResponse from(LabeledEnum value) {
        return new EnumOptionResponse(value.name(), value.displayName());
    }
}
