package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.business.dto.RoomMissionResponse;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.MissionMemberStateReader;
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
    private final MissionMemberStateReader missionMemberStateReader;
    private final Clock clock;

    public RoomMissionService(RoomMissionReader roomMissionReader, MissionReader missionReader,
                              RoomMemberReader roomMemberReader,
                              MissionMemberStateReader missionMemberStateReader,
                              Clock clock) {
        this.roomMissionReader = roomMissionReader;
        this.missionReader = missionReader;
        this.roomMemberReader = roomMemberReader;
        this.missionMemberStateReader = missionMemberStateReader;
        this.clock = clock;
    }

    /** 지금 열려 있는 미션 카드(문구 포함). 방 멤버만 조회 가능. /today 미션 노출용. */
    public List<RoomMissionResponse> getOpenMissions(Long roomId, Long userId) {
        var member = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트(비멤버면 예외)
        List<RoomMission> open = roomMissionReader.getOpenMissions(roomId, LocalDateTime.now(clock));
        if (open.isEmpty()) {
            return List.of();
        }
        List<RoomMission> targeted = open.stream()
                .filter(roomMission -> member.joinedAt() != null
                        && !member.joinedAt().isAfter(roomMission.opensAt()))
                .toList();
        if (targeted.isEmpty()) {
            return List.of();
        }
        Set<Long> openIds = targeted.stream().map(RoomMission::id).collect(Collectors.toSet());
        Set<Long> resolvedIds = missionMemberStateReader.getResolvedRoomMissionIds(member.id(), openIds);
        List<RoomMission> visible = targeted.stream()
                .filter(roomMission -> !resolvedIds.contains(roomMission.id()))
                .toList();
        if (visible.isEmpty()) {
            return List.of();
        }
        Set<Long> revisionIds = visible.stream().map(RoomMission::missionRevisionId).collect(Collectors.toSet());
        Map<Long, MissionRevision> revisionsById = missionReader.getRevisionsByIds(revisionIds).stream()
                .collect(Collectors.toMap(MissionRevision::id, Function.identity(), (a, b) -> a));
        return visible.stream()
                .filter(rm -> revisionsById.containsKey(rm.missionRevisionId()))
                .map(rm -> RoomMissionResponse.of(rm, revisionsById.get(rm.missionRevisionId())))
                .toList();
    }
}
