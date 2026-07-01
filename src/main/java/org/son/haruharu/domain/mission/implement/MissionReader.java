package org.son.haruharu.domain.mission.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.storage.exception.MissionNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MissionReader {

    private final MissionRepository missionRepository;

    /** 활성 미션 후보 풀(MVP: 전역 풀 노출). 방/Day/슬롯별 고정이 필요해지면 MissionCandidateStrategy로 확장. */
    public List<Mission> getActiveCandidates() {
        return missionRepository.findActive();
    }

    public Mission getById(UUID id) {
        return missionRepository.findById(id)
                .orElseThrow(MissionNotFoundException::new);
    }
}
