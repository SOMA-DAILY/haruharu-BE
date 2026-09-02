package org.son.haruharu.domain.mission.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoomMissionRepositoryImpl implements RoomMissionRepository {

    private final RoomMissionJpaRepository jpaRepository;

    @Override
    public RoomMission save(RoomMission roomMission) {
        return jpaRepository.saveAndFlush(RoomMissionEntity.from(roomMission)).toDomain();
    }

    @Override
    public Optional<RoomMission> findById(Long id) {
        return jpaRepository.findById(id).map(RoomMissionEntity::toDomain);
    }

    @Override
    public boolean existsByRoomMissionSlotId(Long roomMissionSlotId) {
        return jpaRepository.existsByRoomMissionSlotId(roomMissionSlotId);
    }

    @Override
    public List<RoomMission> findByRoomId(Long roomId) {
        return jpaRepository.findByRoomIdOrderBySequenceAsc(roomId).stream()
                .map(RoomMissionEntity::toDomain)
                .toList();
    }
}
