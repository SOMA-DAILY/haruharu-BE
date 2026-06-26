package org.son.haruharu.domain.mission.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MissionJpaRepository extends JpaRepository<MissionEntity, UUID> {

    List<MissionEntity> findByIsActiveTrue();
}
