package org.son.haruharu.domain.mission.implement;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MissionOutcomeReader {

    private final MissionOutcomeRepository repository;

    public MissionOutcomeReader(MissionOutcomeRepository repository) {
        this.repository = repository;
    }

    /** 같은 집계 버전으로 이미 확정됐는지. 재집계 멱등 게이트. */
    public boolean isConfirmed(Long roomMissionId, String metricSchemaVersion) {
        return repository.existsByRoomMissionIdAndMetricSchemaVersion(roomMissionId, metricSchemaVersion);
    }

    public Optional<MissionOutcome> find(Long roomMissionId, String metricSchemaVersion) {
        return repository.findByRoomMissionIdAndMetricSchemaVersion(roomMissionId, metricSchemaVersion);
    }
}
