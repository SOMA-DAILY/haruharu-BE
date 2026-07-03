package org.son.haruharu.domain.room.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchResultJpaRepository extends JpaRepository<MatchResultEntity, Long> {

    Optional<MatchResultEntity> findByMemberAIdOrMemberBId(Long memberAId, Long memberBId);
}
