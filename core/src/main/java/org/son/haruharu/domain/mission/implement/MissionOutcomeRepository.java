package org.son.haruharu.domain.mission.implement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MissionOutcomeRepository {

    boolean existsByRoomMissionIdAndMetricSchemaVersion(Long roomMissionId, String metricSchemaVersion);

    Optional<MissionOutcome> findByRoomMissionIdAndMetricSchemaVersion(
            Long roomMissionId, String metricSchemaVersion);

    MissionOutcome save(MissionOutcome outcome);

    /** 방 상태 집계용 — 여러 미션의 확정된 outcome 을 한 번에 읽는다. */
    List<MissionOutcome> findByRoomMissionIdInAndMetricSchemaVersion(
            Collection<Long> roomMissionIds, String metricSchemaVersion);
}
