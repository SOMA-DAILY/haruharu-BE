package org.son.haruharu.domain.mission.storage;

import java.util.List;

import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionSlotRepository;
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
}
