package org.son.haruharu.common.enums;

/**
 * 코드 + 한글 라벨을 함께 노출하는 선택지형 enum 계약.
 * name()은 Enum이 final로 제공하므로 구현체는 displayName()만 정의한다.
 * 메타 API(GET /api/v1/meta/enums)가 이 계약을 통해 프론트 드롭다운 목록을 내려준다.
 */
public interface LabeledEnum {

    String name();

    String displayName();
}
