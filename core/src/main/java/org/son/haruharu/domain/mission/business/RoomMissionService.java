package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.business.dto.RoomMissionResponse;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 방별 미션 인스턴스 조회. 슬롯 배정은 JIT 배치의 {@link MissionAssignmentExecutor}가 담당한다.
 */
@Service
@Transactional(readOnly = true)
public class RoomMissionService {

    private final RoomMissionReader roomMissionReader;
    private final MissionReader missionReader;
    private final RoomMemberReader roomMemberReader;
    private final Clock clock;

    public RoomMissionService(RoomMissionReader roomMissionReader, MissionReader missionReader,
                              RoomMemberReader roomMemberReader, Clock clock) {
        this.roomMissionReader = roomMissionReader;
        this.missionReader = missionReader;
        this.roomMemberReader = roomMemberReader;
        this.clock = clock;
    }

    /** 지금 열려 있는 미션 카드(문구 포함). 방 멤버만 조회 가능. /today 미션 노출용. */
    public List<RoomMissionResponse> getOpenMissions(Long roomId, Long userId) {
        roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트(비멤버면 예외)
        List<RoomMission> open = roomMissionReader.getOpenMissions(roomId, LocalDateTime.now(clock));
        if (open.isEmpty()) {
            return List.of();
        }
        Set<Long> revisionIds = open.stream().map(RoomMission::missionRevisionId).collect(Collectors.toSet());
        Map<Long, MissionRevision> revisionsById = missionReader.getRevisionsByIds(revisionIds).stream()
                .collect(Collectors.toMap(MissionRevision::id, Function.identity(), (a, b) -> a));
        return open.stream()
                .filter(rm -> revisionsById.containsKey(rm.missionRevisionId()))
                .map(rm -> RoomMissionResponse.of(rm, revisionsById.get(rm.missionRevisionId())))
                .toList();
    }
}
