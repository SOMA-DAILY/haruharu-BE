package org.son.haruharu.domain.mission.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RoomMissionReader {

    private final RoomMissionRepository roomMissionRepository;

    /** 방에 미션 인스턴스가 이미 배정됐는지. 자동 배정 재발행 멱등 게이트. */
    public boolean isAssigned(Long roomId) {
        return roomMissionRepository.existsByRoomId(roomId);
    }

    /** 지금 열려 있는 미션들(opensAt ≤ now < closesAt, sequence 오름차순). /today 미션 카드용. */
    public List<RoomMission> getOpenMissions(Long roomId, LocalDateTime now) {
        return roomMissionRepository.findByRoomId(roomId).stream()
                .filter(mission -> mission.isOpenAt(now))
                .toList();
    }
}
