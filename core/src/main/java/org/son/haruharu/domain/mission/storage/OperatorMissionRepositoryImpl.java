package org.son.haruharu.domain.mission.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.implement.OperatorMission;
import org.son.haruharu.domain.mission.implement.OperatorMissionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OperatorMissionRepositoryImpl implements OperatorMissionRepository {
    private final OperatorMissionJpaRepository repository;
    public OperatorMission save(OperatorMission m) { return repository.save(OperatorMissionEntity.from(m)).toDomain(); }
    public Optional<OperatorMission> findForUpdate(Long id) { return repository.findForUpdate(id).map(OperatorMissionEntity::toDomain); }
    public Optional<OperatorMission> findByRequestKey(Long roomId, String key) {
        return repository.findByRoomIdAndRequestKey(roomId, key).map(OperatorMissionEntity::toDomain);
    }
    public List<OperatorMission> findHistory(Long roomId, Long cursor, int limit) {
        return repository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, cursor == null ? Long.MAX_VALUE : cursor,
                PageRequest.of(0, limit)).stream().map(OperatorMissionEntity::toDomain).toList();
    }
    public List<Long> findDueIds(LocalDateTime now, int limit) { return repository.findDueIds(now, PageRequest.of(0, limit)); }
    public List<Long> findPendingDeleteIds(int limit) { return repository.findPendingDeleteIds(PageRequest.of(0, limit)); }
}
