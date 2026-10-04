package org.son.haruharu.domain.mission.storage;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OperatorMissionJpaRepository extends JpaRepository<OperatorMissionEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from OperatorMissionEntity m where m.id = :id")
    Optional<OperatorMissionEntity> findForUpdate(@Param("id") Long id);
    Optional<OperatorMissionEntity> findByRoomIdAndRequestKey(Long roomId, String requestKey);
    List<OperatorMissionEntity> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long cursor, Pageable pageable);
    @Query("select m.id from OperatorMissionEntity m where m.status in "
            + "(org.son.haruharu.domain.mission.implement.OperatorMissionStatus.SCHEDULED, "
            + "org.son.haruharu.domain.mission.implement.OperatorMissionStatus.SENDING) "
            + "and m.scheduledAt <= :now order by m.scheduledAt, m.id")
    List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable pageable);
    @Query("select m.id from OperatorMissionEntity m where m.deleteRequestedAt is not null "
            + "and m.deleteProcessedAt is null order by m.id")
    List<Long> findPendingDeleteIds(Pageable pageable);
}
