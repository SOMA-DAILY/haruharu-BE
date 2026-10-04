package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.storage.exception.OperatorMissionNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OperatorMissionReader {
    private final OperatorMissionRepository repository;
    public OperatorMission getForUpdate(Long id) {
        return repository.findForUpdate(id).orElseThrow(OperatorMissionNotFoundException::new);
    }
    public Optional<OperatorMission> findByRequestKey(Long roomId, String key) { return repository.findByRequestKey(roomId, key); }
    public List<OperatorMission> history(Long roomId, Long cursor, int limit) { return repository.findHistory(roomId, cursor, limit); }
    public List<Long> dueIds(LocalDateTime now, int limit) { return repository.findDueIds(now, limit); }
    public List<Long> pendingDeleteIds(int limit) { return repository.findPendingDeleteIds(limit); }
}
