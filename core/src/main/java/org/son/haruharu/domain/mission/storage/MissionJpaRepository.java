package org.son.haruharu.domain.mission.storage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionJpaRepository extends JpaRepository<MissionEntity, Long> {
}
