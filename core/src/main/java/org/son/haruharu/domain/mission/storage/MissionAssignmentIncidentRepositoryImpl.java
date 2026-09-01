package org.son.haruharu.domain.mission.storage;

import org.son.haruharu.domain.mission.implement.MissionAssignmentIncident;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MissionAssignmentIncidentRepositoryImpl implements MissionAssignmentIncidentRepository {

    private final MissionAssignmentIncidentJpaRepository jpaRepository;

    public MissionAssignmentIncidentRepositoryImpl(MissionAssignmentIncidentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<MissionAssignmentIncident> findByRoomMissionSlotId(Long roomMissionSlotId) {
        return jpaRepository.findByRoomMissionSlotId(roomMissionSlotId)
                .map(MissionAssignmentIncidentEntity::toDomain);
    }

    @Override
    public MissionAssignmentIncident save(MissionAssignmentIncident incident) {
        return jpaRepository.save(MissionAssignmentIncidentEntity.from(incident)).toDomain();
    }
}
