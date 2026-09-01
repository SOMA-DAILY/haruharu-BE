package org.son.haruharu.domain.mission.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomMissionWriter {

    private final RoomMissionRepository roomMissionRepository;

    public RoomMission save(RoomMission roomMission) {
        return roomMissionRepository.save(roomMission);
    }
}
