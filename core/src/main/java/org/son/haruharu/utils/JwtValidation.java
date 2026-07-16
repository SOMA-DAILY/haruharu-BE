package org.son.haruharu.utils;

/**
 * JWT 검증 결과. 실패 사유를 호출자(필터·EntryPoint)까지 전달해 401 로그에 원인을 남기기 위한 타입이다(#259).
 *
 * 사유는 <b>로그 전용</b>이다 — 클라이언트 응답은 "인증이 필요합니다."로 뭉뚱그린다.
 * 만료인지 서명 불일치인지를 응답으로 알려주면 토큰 대조·계정 열거에 악용될 수 있다.
 */
public enum JwtValidation {

    VALID,
    /** 서명·형식은 정상이나 exp 경과. 클라이언트가 refresh 로 이어가야 하는 정상 흐름. */
    EXPIRED,
    /** 서명 검증 실패. 시크릿 불일치(배포 환경 드리프트) 또는 위조 시도. */
    BAD_SIGNATURE,
    /** JWT 구조 자체가 깨짐. 쓰레기값·빈 문자열·잘린 토큰. */
    MALFORMED
}
