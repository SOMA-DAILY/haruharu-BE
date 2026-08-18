package org.son.haruharu.admin.domain.mission.storage;

import org.son.haruharu.domain.mission.storage.MissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminMissionJpaRepository extends JpaRepository<MissionEntity, Long> {
    boolean existsByDescription(String description);
}
