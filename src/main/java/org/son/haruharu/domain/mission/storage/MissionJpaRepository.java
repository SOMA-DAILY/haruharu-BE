package org.son.haruharu.domain.mission.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MissionJpaRepository extends JpaRepository<MissionEntity, Long> {

    List<MissionEntity> findByIsActiveTrue();
}
