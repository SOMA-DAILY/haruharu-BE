package org.son.haruharu.domain.mission.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface MissionRevisionJpaRepository extends JpaRepository<MissionRevisionEntity, Long> {

    @Query("""
            select revision
            from MissionRevisionEntity revision, MissionEntity mission
            where revision.id = mission.activeRevisionId
              and mission.active = true
              and revision.fallback = false
            """)
    List<MissionRevisionEntity> findActiveCurrentRevisions();

    List<MissionRevisionEntity> findByIdIn(Collection<Long> ids);

    List<MissionRevisionEntity> findByMissionIdOrderByRevisionNumberDesc(Long missionId, Pageable pageable);

    java.util.Optional<MissionRevisionEntity> findTopByMissionIdOrderByRevisionNumberDesc(Long missionId);

    long countByMissionId(Long missionId);
}
