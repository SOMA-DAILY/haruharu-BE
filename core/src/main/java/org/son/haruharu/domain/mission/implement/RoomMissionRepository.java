package org.son.haruharu.domain.mission.implement;

import java.util.List;

public interface RoomMissionRepository {

    List<RoomMission> saveAll(List<RoomMission> roomMissions);

    boolean existsByRoomId(Long roomId);

    /** 방의 미션 인스턴스 전체(sequence 오름차순). */
    List<RoomMission> findByRoomId(Long roomId);
}
