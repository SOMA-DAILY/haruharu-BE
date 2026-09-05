package org.son.haruharu.domain.profile.implement;

/**
 * 프로필 성별(#392). 온보딩에서 자기신고로 수집한다. 3:3 매칭·동성선택 차단·방 가명 배정의 소스.
 * 검증된 성별(PASS 본인인증)의 단일 출처는 여전히 identity 도메인(user_identities.gender)이며, 이 값은 그와 별개다.
 */
public enum Gender {
    MALE, FEMALE
}
