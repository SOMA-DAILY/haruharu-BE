package org.son.haruharu.domain.mission.storage;

import java.util.List;
import java.time.LocalDateTime;

import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentStatus;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionSlotRepository;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class RoomMissionSlotRepositoryImpl implements RoomMissionSlotRepository {

    private final RoomMissionSlotJpaRepository jpaRepository;

    public RoomMissionSlotRepositoryImpl(RoomMissionSlotJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<RoomMissionSlot> saveAll(List<RoomMissionSlot> slots) {
        return jpaRepository.saveAll(slots.stream().map(RoomMissionSlotEntity::from).toList()).stream()
                .map(RoomMissionSlotEntity::toDomain)
                .toList();
    }

    @Override
    public List<RoomMissionSlot> findByRoomId(Long roomId) {
        return jpaRepository.findByRoomIdOrderByOpensAtAsc(roomId).stream()
                .map(RoomMissionSlotEntity::toDomain)
                .toList();
    }

    @Override
    public List<RoomMissionSlot> findDueUnassigned(
            LocalDateTime now, LocalDateTime assignmentCutoff, int limit) {
        return jpaRepository.findDueUnassigned(
                        now, assignmentCutoff, RoomStatus.CANCELLED, PageRequest.of(0, limit)).stream()
                .map(RoomMissionSlotEntity::toDomain)
                .toList();
    }

    @Override
    public List<RoomMissionSlot> findExpiredUnassigned(LocalDateTime now, int limit) {
        return jpaRepository.findExpiredUnassigned(
                        now, MissionAssignmentIncidentStatus.FAILED,
                        RoomStatus.CANCELLED, PageRequest.of(0, limit)).stream()
                .map(RoomMissionSlotEntity::toDomain)
                .toList();
    }
}
