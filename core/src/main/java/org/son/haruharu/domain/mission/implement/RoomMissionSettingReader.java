package org.son.haruharu.domain.mission.implement;

import org.springframework.stereotype.Component;

@Component
public class RoomMissionSettingReader {

    private final RoomMissionSettingRepository roomMissionSettingRepository;

    public RoomMissionSettingReader(RoomMissionSettingRepository roomMissionSettingRepository) {
        this.roomMissionSettingRepository = roomMissionSettingRepository;
    }

    /** 설정 행이 없는 방은 미션이 꺼진 방으로 본다 — 미션은 방 생성 시 명시적으로 켜야 한다. */
    public boolean isMissionEnabled(Long roomId) {
        return roomMissionSettingRepository.findByRoomId(roomId)
                .map(RoomMissionSetting::missionEnabled)
                .orElse(false);
    }
}
