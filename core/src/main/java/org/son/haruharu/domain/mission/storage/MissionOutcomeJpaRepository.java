package org.son.haruharu.domain.mission.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MissionOutcomeJpaRepository extends JpaRepository<MissionOutcomeEntity, Long> {

    boolean existsByRoomMissionIdAndMetricSchemaVersion(Long roomMissionId, String metricSchemaVersion);

    Optional<MissionOutcomeEntity> findByRoomMissionIdAndMetricSchemaVersion(
            Long roomMissionId, String metricSchemaVersion);
}
