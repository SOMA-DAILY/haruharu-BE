package org.son.haruharu.domain.auth.implement;

/**
 * OAuth 공급자 유형. 요청 바디는 이 enum으로 직접 역직렬화하며(스웨거 자동 노출 + 서버 검증),
 * 잘못된 값은 Jackson 역직렬화 단계에서 400으로 거부된다. 대소문자 무시는 jackson 전역 설정으로 처리.
 */
public enum ProviderType {
    KAKAO, APPLE
}
