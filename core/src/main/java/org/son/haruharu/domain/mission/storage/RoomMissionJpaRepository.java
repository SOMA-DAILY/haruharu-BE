package org.son.haruharu.domain.mission.storage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RoomMissionJpaRepository extends JpaRepository<RoomMissionEntity, Long> {

    boolean existsByRoomId(Long roomId);

    boolean existsByRoomMissionSlotId(Long roomMissionSlotId);

    List<RoomMissionEntity> findByRoomIdOrderBySequenceAsc(Long roomId);

    /**
     * 시간창이 닫혔는데 해당 집계 버전 outcome 이 없는 미션. 슬롯을 조인해 슬롯의 closes_at 인덱스를 탄다.
     * soft-delete 필터는 Hibernate 가 JPQL 에 자동으로 붙인다.
     */
    @Query("""
            select roomMission from RoomMissionEntity roomMission
            join RoomMissionSlotEntity slot on slot.id = roomMission.roomMissionSlotId
            where slot.closesAt <= :closedBefore
              and not exists (
                  select 1 from MissionOutcomeEntity outcome
                  where outcome.roomMissionId = roomMission.id
                    and outcome.metricSchemaVersion = :metricSchemaVersion)
            order by slot.closesAt asc, roomMission.id asc
            """)
    List<RoomMissionEntity> findClosedWithoutOutcome(
            @Param("closedBefore") LocalDateTime closedBefore,
            @Param("metricSchemaVersion") String metricSchemaVersion,
            Pageable pageable);
}
