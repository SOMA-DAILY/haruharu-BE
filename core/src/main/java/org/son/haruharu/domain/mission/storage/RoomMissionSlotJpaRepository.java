package org.son.haruharu.domain.mission.storage;

import java.util.List;
import java.time.LocalDateTime;

import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentStatus;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomMissionSlotJpaRepository extends JpaRepository<RoomMissionSlotEntity, Long> {

    List<RoomMissionSlotEntity> findByRoomIdOrderByOpensAtAsc(Long roomId);

    @Query("""
            select slot
            from RoomMissionSlotEntity slot
            where slot.opensAt <= :assignmentCutoff
              and slot.closesAt > :now
              and exists (
                  select room.id
                  from RoomEntity room
                  where room.id = slot.roomId
                    and room.status <> :cancelledStatus
              )
              and not exists (
                  select mission.id
                  from RoomMissionEntity mission
                  where mission.roomMissionSlotId = slot.id
              )
            order by slot.opensAt, slot.id
            """)
    List<RoomMissionSlotEntity> findDueUnassigned(
            @Param("now") LocalDateTime now,
            @Param("assignmentCutoff") LocalDateTime assignmentCutoff,
            @Param("cancelledStatus") RoomStatus cancelledStatus,
            Pageable pageable);

    @Query("""
            select slot
            from RoomMissionSlotEntity slot
            where slot.closesAt <= :now
              and exists (
                  select room.id
                  from RoomEntity room
                  where room.id = slot.roomId
                    and room.status <> :cancelledStatus
              )
              and not exists (
                  select mission.id
                  from RoomMissionEntity mission
                  where mission.roomMissionSlotId = slot.id
              )
              and not exists (
                  select incident.id
                  from MissionAssignmentIncidentEntity incident
                  where incident.roomMissionSlotId = slot.id
                    and incident.status = :failedStatus
              )
            order by slot.closesAt, slot.id
            """)
    List<RoomMissionSlotEntity> findExpiredUnassigned(
            @Param("now") LocalDateTime now,
            @Param("failedStatus") MissionAssignmentIncidentStatus failedStatus,
            @Param("cancelledStatus") RoomStatus cancelledStatus,
            Pageable pageable);
}
