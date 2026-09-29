package org.son.haruharu.common;

/** 칸별 검증 메시지(#455). field 는 요청 JSON 키(snake_case)다. 화면이 이 칸 아래에 message 를 그대로 보여준다. */
public record ApiFieldError(String field, String message) {}
