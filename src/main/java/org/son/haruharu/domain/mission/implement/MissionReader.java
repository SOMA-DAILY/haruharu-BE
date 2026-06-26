package org.son.haruharu.domain.mission.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MissionReader {

    private final MissionRepository missionRepository;

    /** 활성 미션 후보 풀(MVP: 전역 풀 노출). 방/Day/슬롯별 고정이 필요해지면 MissionCandidateStrategy로 확장. */
    public List<Mission> getActiveCandidates() {
        return missionRepository.findActive();
    }
}
