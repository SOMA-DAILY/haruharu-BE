package org.son.haruharu.domain.mission.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.storage.exception.MissionNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

// MVP 미션 비활성화 동안 활성 소비자 없음(도메인 유지). 미션 재개 시 RoomToday/Video 흐름에서 다시 주입된다.
@Component
@RequiredArgsConstructor
public class MissionReader {

    private final MissionRepository missionRepository;

    public List<MissionCatalogEntry> getActiveCatalog() {
        return missionRepository.findActiveCatalog();
    }

    public MissionRevision getRevisionById(Long id) {
        return missionRepository.findRevisionById(id)
                .orElseThrow(MissionNotFoundException::new);
    }

    public List<MissionRevision> getRevisionsByIds(Set<Long> revisionIds) {
        return missionRepository.findRevisionsByIds(revisionIds);
    }
}
