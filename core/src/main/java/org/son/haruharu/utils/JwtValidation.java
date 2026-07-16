package org.son.haruharu.utils;

/**
 * JWT 검증 결과. 실패 사유를 EntryPoint 까지 전달해 401 로그에 원인을 남기기 위한 타입이다(#259).
 * 사유는 <b>로그 전용</b> — 응답으로 노출하면 토큰 대조·계정 열거에 악용된다.
 */
public enum JwtValidation {

    VALID,
    /** 서명은 정상이나 exp 경과. refresh 로 이어가는 정상 흐름. */
    EXPIRED,
    /** 서명 검증 실패. 시크릿 드리프트 또는 위조 시도. */
    BAD_SIGNATURE,
    /** JWT 구조가 깨짐. 쓰레기값·빈 문자열·잘린 토큰. */
    MALFORMED
}
