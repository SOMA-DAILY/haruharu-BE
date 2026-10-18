package org.son.haruharu.domain.identity.business.event;

/**
 * PASS 본인인증 정보가 저장됐다(#512). 커밋 후 기수 신청 판정의 트리거다.
 * 성별·생년은 싣지 않는다 — 받는 쪽이 커밋된 user_identities 를 다시 읽어 조회 재판정과 같은 값을 쓴다.
 */
public record IdentityVerifiedEvent(Long userId) {
}
