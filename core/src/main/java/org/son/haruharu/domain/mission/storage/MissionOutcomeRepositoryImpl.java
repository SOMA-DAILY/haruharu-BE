package org.son.haruharu.domain.mission.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.implement.MissionOutcome;
import org.son.haruharu.domain.mission.implement.MissionOutcomeRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MissionOutcomeRepositoryImpl implements MissionOutcomeRepository {

    private final MissionOutcomeJpaRepository jpaRepository;

    @Override
    public boolean existsByRoomMissionIdAndMetricSchemaVersion(
            Long roomMissionId, String metricSchemaVersion) {
        return jpaRepository.existsByRoomMissionIdAndMetricSchemaVersion(roomMissionId, metricSchemaVersion);
    }

    @Override
    public Optional<MissionOutcome> findByRoomMissionIdAndMetricSchemaVersion(
            Long roomMissionId, String metricSchemaVersion) {
        return jpaRepository.findByRoomMissionIdAndMetricSchemaVersion(roomMissionId, metricSchemaVersion)
                .map(MissionOutcomeEntity::toDomain);
    }

    @Override
    public List<MissionOutcome> findByRoomMissionIdInAndMetricSchemaVersion(
            Collection<Long> roomMissionIds, String metricSchemaVersion) {
        if (roomMissionIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository
                .findByRoomMissionIdInAndMetricSchemaVersion(roomMissionIds, metricSchemaVersion)
                .stream()
                .map(MissionOutcomeEntity::toDomain)
                .toList();
    }

    @Override
    public MissionOutcome save(MissionOutcome outcome) {
        return jpaRepository.saveAndFlush(MissionOutcomeEntity.from(outcome)).toDomain();
    }
}
