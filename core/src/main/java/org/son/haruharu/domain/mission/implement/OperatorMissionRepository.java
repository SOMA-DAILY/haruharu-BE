package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OperatorMissionRepository {
    OperatorMission save(OperatorMission mission);
    Optional<OperatorMission> findForUpdate(Long id);
    Optional<OperatorMission> findByRequestKey(Long roomId, String requestKey);
    List<OperatorMission> findHistory(Long roomId, Long cursor, int limit);
    List<Long> findDueIds(LocalDateTime now, int limit);
    List<Long> findPendingDeleteIds(int limit);
}
