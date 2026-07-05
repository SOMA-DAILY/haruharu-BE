package org.son.haruharu.domain.value.implement;

/** 2지선다 가치관 질문. optionA/optionB가 선택지 텍스트, 답변(UserValueAnswer)은 선택한 쪽 문자열을 그대로 저장한다. */
public record ValueQuestion(
        Long id,
        String questionText,
        String optionA,
        String optionB,
        String category,
        int displayOrder,
        boolean isActive
) {}
