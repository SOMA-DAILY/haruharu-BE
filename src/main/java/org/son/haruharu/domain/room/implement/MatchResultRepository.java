package org.son.haruharu.domain.room.implement;

import java.util.Optional;

public interface MatchResultRepository {

    Optional<MatchResult> findByMember(Long memberId);

    /** 두 멤버가 서로 최종매칭(1쌍 1행) 됐는지. 순서 무관(내부에서 canonical 정렬). */
    boolean existsBetween(Long memberId1, Long memberId2);

    MatchResult save(MatchResult matchResult);
}
