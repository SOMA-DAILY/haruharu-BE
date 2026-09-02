package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RoomMissionRepository {

    RoomMission save(RoomMission roomMission);

    Optional<RoomMission> findById(Long id);

    boolean existsByRoomMissionSlotId(Long roomMissionSlotId);

    /** 방의 미션 인스턴스 전체(sequence 오름차순). */
    List<RoomMission> findByRoomId(Long roomId);

    /**
     * 시간창이 {@code closedBefore} 이전에 닫혔고 해당 집계 버전의 outcome 이 아직 없는 미션.
     * 슬롯의 closes_at 기준으로 조회해 #365 의 idx_room_mission_slots_closes_at 을 재사용한다.
     */
    List<RoomMission> findClosedWithoutOutcome(
            LocalDateTime closedBefore, String metricSchemaVersion, int batchSize);
}
