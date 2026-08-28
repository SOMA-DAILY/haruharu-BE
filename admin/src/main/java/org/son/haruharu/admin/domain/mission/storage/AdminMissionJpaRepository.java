package org.son.haruharu.admin.domain.mission.storage;

import org.son.haruharu.domain.mission.storage.MissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface AdminMissionJpaRepository extends JpaRepository<MissionEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mission from MissionEntity mission where mission.id = :id")
    Optional<MissionEntity> findByIdForUpdate(Long id);
}
