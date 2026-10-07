package org.son.haruharu.domain.mission.common.storage;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.RoomMissionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RoomMissionRepositoryImpl implements RoomMissionRepository {
    private final RoomMissionJpaRepository repository;

    @Override
    public RoomMission save(RoomMission mission) {
        return repository.save(RoomMissionEntity.from(mission)).toDomain();
    }

    @Override
    public Optional<RoomMission> findById(Long id) {
        return repository.findById(id).map(RoomMissionEntity::toDomain);
    }

    @Override
    public Optional<RoomMission> findByIdForUpdate(Long id) {
        return repository.findByIdForUpdate(id).map(RoomMissionEntity::toDomain);
    }

    @Override
    public List<RoomMission> findPage(Long roomId, Long afterId, int limit) {
        return repository.findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId, afterId == null ? 0L : afterId,
                PageRequest.of(0, limit)).stream().map(RoomMissionEntity::toDomain).toList();
    }

    @Override
    public List<RoomMission> findByIdsInRoom(Long roomId, Collection<Long> ids) {
        return repository.findByRoomIdAndIdIn(roomId, ids).stream().map(RoomMissionEntity::toDomain).toList();
    }

    @Override
    public List<RoomMission> findByIds(Collection<Long> ids) {
        return repository.findAllById(ids).stream().map(RoomMissionEntity::toDomain).toList();
    }

    @Override
    public List<RoomMission> findVisiblePage(Long roomId, Long afterId, java.time.LocalDateTime now, int limit) {
        // timestamp(6) 바인딩이 기준 시각을 반올림해 미래 행을 포함하지 않도록 내린다.
        return repository.findByRoomIdAndIdGreaterThanAndOpensAtLessThanEqualOrderByIdAsc(
                roomId, afterId == null ? 0L : afterId, now.truncatedTo(ChronoUnit.MICROS), PageRequest.of(0, limit))
                .stream().map(RoomMissionEntity::toDomain).toList();
    }

}
