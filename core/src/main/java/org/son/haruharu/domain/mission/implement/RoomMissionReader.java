package org.son.haruharu.domain.mission.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.storage.exception.RoomMissionNotFoundException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RoomMissionReader {

    private final RoomMissionRepository roomMissionRepository;

    public boolean isAssignedToSlot(Long roomMissionSlotId) {
        return roomMissionRepository.existsByRoomMissionSlotId(roomMissionSlotId);
    }

    public RoomMission getById(Long id) {
        return roomMissionRepository.findById(id)
                .orElseThrow(RoomMissionNotFoundException::new);
    }

    public List<RoomMission> getByRoomId(Long roomId) {
        return roomMissionRepository.findByRoomId(roomId);
    }

    /** 집계 대상 — 시간창이 닫혔는데 해당 버전 outcome 이 아직 없는 미션. */
    public List<RoomMission> getClosedWithoutOutcome(
            LocalDateTime closedBefore, String metricSchemaVersion, int batchSize) {
        return roomMissionRepository.findClosedWithoutOutcome(closedBefore, metricSchemaVersion, batchSize);
    }

    /** 지금 열려 있는 미션들(opensAt ≤ now < closesAt, sequence 오름차순). /today 미션 카드용. */
    public List<RoomMission> getOpenMissions(Long roomId, LocalDateTime now) {
        return roomMissionRepository.findByRoomId(roomId).stream()
                .filter(mission -> mission.isOpenAt(now))
                .toList();
    }
}
