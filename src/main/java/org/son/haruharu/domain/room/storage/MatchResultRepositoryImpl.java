package org.son.haruharu.domain.room.storage;

import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class MatchResultRepositoryImpl implements MatchResultRepository {

    private final MatchResultJpaRepository matchResultJpaRepository;

    public MatchResultRepositoryImpl(MatchResultJpaRepository matchResultJpaRepository) {
        this.matchResultJpaRepository = matchResultJpaRepository;
    }

    @Override
    public Optional<MatchResult> findByMember(UUID memberId) {
        return matchResultJpaRepository.findByMemberAIdOrMemberBId(memberId, memberId).map(MatchResultEntity::toDomain);
    }

    @Override
    public MatchResult save(MatchResult matchResult) {
        return matchResultJpaRepository.save(MatchResultEntity.from(matchResult)).toDomain();
    }
}
