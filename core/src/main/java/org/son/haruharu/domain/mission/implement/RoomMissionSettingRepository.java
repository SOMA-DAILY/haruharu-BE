package org.son.haruharu.domain.mission.implement;

import java.util.Optional;

public interface RoomMissionSettingRepository {

    RoomMissionSetting save(RoomMissionSetting setting);

    Optional<RoomMissionSetting> findByRoomId(Long roomId);
}
