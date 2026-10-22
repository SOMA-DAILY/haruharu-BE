package org.son.haruharu.domain.room.storage;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.room.implement.*;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InteractionGuidanceRepositoryImpl implements InteractionGuidanceRepository {
    private final InteractionExperimentJpaRepository experiments;
    private final InteractionFirstActionsJpaRepository actions;
    private final InteractionGuideClaimJpaRepository claims;

    @Override public Optional<InteractionExperimentAssignment> findAssignment(Long roomId) {
        return experiments.findById(roomId).map(InteractionExperimentEntity::toDomain);
    }
    @Override public void saveAssignment(InteractionExperimentAssignment value) {
        experiments.saveAndFlush(InteractionExperimentEntity.from(value));
    }
    @Override public Optional<InteractionFirstActions> findFirstActions(Long memberId) {
        return actions.findById(memberId).map(InteractionFirstActionsEntity::toDomain);
    }
    @Override public void saveFirstActions(InteractionFirstActions value) {
        actions.saveAndFlush(InteractionFirstActionsEntity.from(value));
    }
    @Override public List<InteractionGuideClaim> findClaims(Long memberId) {
        return claims.findByRoomMemberId(memberId).stream().map(InteractionGuideClaimEntity::toDomain).toList();
    }
    @Override public void saveClaim(InteractionGuideClaim value) {
        claims.saveAndFlush(InteractionGuideClaimEntity.from(value));
    }
}
