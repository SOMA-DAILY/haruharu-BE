package org.son.haruharu.domain.mission.implement;

import java.util.Optional;

public interface MissionOutcomeRepository {

    boolean existsByRoomMissionIdAndMetricSchemaVersion(Long roomMissionId, String metricSchemaVersion);

    Optional<MissionOutcome> findByRoomMissionIdAndMetricSchemaVersion(
            Long roomMissionId, String metricSchemaVersion);

    MissionOutcome save(MissionOutcome outcome);
}
