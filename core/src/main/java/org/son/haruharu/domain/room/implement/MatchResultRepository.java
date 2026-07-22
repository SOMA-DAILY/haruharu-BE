package org.son.haruharu.domain.room.implement;

import java.util.Optional;

public interface MatchResultRepository {

    Optional<MatchResult> findByMember(Long memberId);

    Optional<MatchResult> findById(Long id);

    /** 두 멤버가 서로 최종매칭(1쌍 1행) 됐는지. 순서 무관(내부에서 canonical 정렬). */
    boolean existsBetween(Long memberId1, Long memberId2);

    MatchResult save(MatchResult matchResult);

    /** 데모 정리 전용 하드삭제(#266). 전역 soft-delete + UNIQUE(member_a,member_b)라 재시연 재생성을 위해 물리 삭제한다. */
    void hardDeleteByMemberIds(java.util.List<Long> memberIds);
}
