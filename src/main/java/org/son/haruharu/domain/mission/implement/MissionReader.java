package org.son.haruharu.domain.mission.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.storage.exception.MissionNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

// MVP 미션 비활성화 동안 활성 소비자 없음(도메인 유지). 미션 재개 시 RoomToday/Video 흐름에서 다시 주입된다.
@Component
@RequiredArgsConstructor
public class MissionReader {

    private final MissionRepository missionRepository;

    /** 활성 미션 후보 풀(MVP: 전역 풀 노출). 방/Day/슬롯별 고정이 필요해지면 MissionCandidateStrategy로 확장. */
    public List<Mission> getActiveCandidates() {
        return missionRepository.findActive();
    }

    public Mission getById(Long id) {
        return missionRepository.findById(id)
                .orElseThrow(MissionNotFoundException::new);
    }
}
