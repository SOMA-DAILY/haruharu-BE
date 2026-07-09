package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;

/**
 * 상호 최종선택 성사 결과(1쌍 1행). canonical ordering(memberA < memberB)·중복 방지는 #41 앱 로직에서 보장한다
 * (CHECK·partial unique는 JPA/H2 DDL로 생성되지 않음).
 */
public record MatchResult(
        Long id,
        Long memberAId,
        Long memberBId,
        LocalDateTime matchedAt
) {
    /**
     * 두 멤버로 매칭 결과를 만든다. 입력 순서와 무관하게 memberA &lt; memberB로 정렬해
     * (A,B)/(B,A) 중복 행을 막는다(1쌍 1행).
     */
    public static MatchResult of(Long member1, Long member2, LocalDateTime matchedAt) {
        boolean ordered = member1.compareTo(member2) < 0;
        Long memberA = ordered ? member1 : member2;
        Long memberB = ordered ? member2 : member1;
        return new MatchResult(null, memberA, memberB, matchedAt);
    }
}
