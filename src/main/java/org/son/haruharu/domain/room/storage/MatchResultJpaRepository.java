package org.son.haruharu.domain.room.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchResultJpaRepository extends JpaRepository<MatchResultEntity, UUID> {

    Optional<MatchResultEntity> findByMemberAIdOrMemberBId(UUID memberAId, UUID memberBId);
}
