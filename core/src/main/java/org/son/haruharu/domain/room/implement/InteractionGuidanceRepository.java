package org.son.haruharu.domain.room.implement;

import java.util.List;
import java.util.Optional;

public interface InteractionGuidanceRepository {
    Optional<InteractionExperimentAssignment> findAssignment(Long roomId);
    void saveAssignment(InteractionExperimentAssignment assignment);
    Optional<InteractionFirstActions> findFirstActions(Long roomMemberId);
    void saveFirstActions(InteractionFirstActions actions);
    List<InteractionGuideClaim> findClaims(Long roomMemberId);
    void saveClaim(InteractionGuideClaim claim);
}
