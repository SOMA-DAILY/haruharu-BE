package org.son.haruharu.domain.mission.common.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomMissionWriter {
    private final RoomMissionRepository repository;

    public RoomMission save(RoomMission mission) {
        return repository.save(mission);
    }
}
