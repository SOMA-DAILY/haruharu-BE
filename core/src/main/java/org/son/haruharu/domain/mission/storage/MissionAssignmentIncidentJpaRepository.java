package org.son.haruharu.domain.mission.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MissionAssignmentIncidentJpaRepository
        extends JpaRepository<MissionAssignmentIncidentEntity, Long> {

    Optional<MissionAssignmentIncidentEntity> findByRoomMissionSlotId(Long roomMissionSlotId);
}
