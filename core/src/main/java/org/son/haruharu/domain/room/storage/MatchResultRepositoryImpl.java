package org.son.haruharu.domain.room.storage;

import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MatchResultRepositoryImpl implements MatchResultRepository {

    private final MatchResultJpaRepository matchResultJpaRepository;

    public MatchResultRepositoryImpl(MatchResultJpaRepository matchResultJpaRepository) {
        this.matchResultJpaRepository = matchResultJpaRepository;
    }

    @Override
    public Optional<MatchResult> findByMember(Long memberId) {
        return matchResultJpaRepository.findByMemberAIdOrMemberBId(memberId, memberId).map(MatchResultEntity::toDomain);
    }

    @Override
    public Optional<MatchResult> findById(Long id) {
        return matchResultJpaRepository.findById(id).map(MatchResultEntity::toDomain);
    }

    @Override
    public boolean existsBetween(Long memberId1, Long memberId2) {
        // 저장은 memberA < memberB canonical이라 조회도 동일하게 정렬해 대조한다.
        long a = Math.min(memberId1, memberId2);
        long b = Math.max(memberId1, memberId2);
        return matchResultJpaRepository.existsByMemberAIdAndMemberBId(a, b);
    }

    @Override
    public MatchResult save(MatchResult matchResult) {
        return matchResultJpaRepository.save(MatchResultEntity.from(matchResult)).toDomain();
    }

    @Override
    public void hardDeleteByMemberIds(java.util.List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return;
        }
        matchResultJpaRepository.hardDeleteByMemberIds(memberIds);
    }
}
