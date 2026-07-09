package org.son.haruharu.domain.mission.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MissionWriter {

    private final MissionRepository missionRepository;

    public List<Mission> saveAll(List<Mission> missions) {
        return missionRepository.saveAll(missions);
    }
}
