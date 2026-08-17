package org.son.haruharu.domain.mission.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RoomMissionWriter {

    private final RoomMissionRepository roomMissionRepository;

    public List<RoomMission> saveAll(List<RoomMission> roomMissions) {
        return roomMissionRepository.saveAll(roomMissions);
    }
}
