package org.son.haruharu.domain.mission.common.storage;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomMissionJpaRepository extends JpaRepository<RoomMissionEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from RoomMissionEntity m where m.id = :id")
    Optional<RoomMissionEntity> findByIdForUpdate(@Param("id") Long id);

    List<RoomMissionEntity> findByRoomIdAndIdGreaterThanOrderByIdAsc(Long roomId, Long afterId, Pageable pageable);

    List<RoomMissionEntity> findByRoomIdAndIdIn(Long roomId, Collection<Long> ids);

    List<RoomMissionEntity> findByRoomIdAndTypeOrderByIdAsc(Long roomId, MissionType type);

    List<RoomMissionEntity> findByRoomIdAndIdGreaterThanAndOpensAtLessThanEqualOrderByIdAsc(
            Long roomId, Long afterId, java.time.LocalDateTime now, Pageable pageable);

}
