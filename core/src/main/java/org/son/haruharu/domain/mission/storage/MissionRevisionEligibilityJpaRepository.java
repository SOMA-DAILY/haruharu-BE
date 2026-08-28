package org.son.haruharu.domain.mission.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;

public interface MissionRevisionEligibilityJpaRepository
        extends JpaRepository<MissionRevisionEligibilityEntity, Long> {

    List<MissionRevisionEligibilityEntity> findByMissionRevisionIdOrderByDayNumberAscPeriodAsc(
            Long missionRevisionId);

    List<MissionRevisionEligibilityEntity> findByMissionRevisionIdIn(Collection<Long> missionRevisionIds);
}
