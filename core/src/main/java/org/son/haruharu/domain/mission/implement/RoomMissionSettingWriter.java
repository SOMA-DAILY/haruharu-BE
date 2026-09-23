package org.son.haruharu.domain.mission.implement;

import org.springframework.stereotype.Component;

@Component
public class RoomMissionSettingWriter {

    private final RoomMissionSettingRepository roomMissionSettingRepository;

    public RoomMissionSettingWriter(RoomMissionSettingRepository roomMissionSettingRepository) {
        this.roomMissionSettingRepository = roomMissionSettingRepository;
    }

    public RoomMissionSetting save(RoomMissionSetting setting) {
        return roomMissionSettingRepository.save(setting);
    }
}
