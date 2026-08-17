package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.business.dto.RoomMissionResponse;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.implement.RoomMissionWriter;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 방별 미션 인스턴스 배정·조회. 배정 규칙은 {@link MissionAssigner}에 위임하고, 이 서비스는 멱등·저장·조회만 담당한다.
 */
@Service
@Transactional(readOnly = true)
public class RoomMissionService {

    private final MissionAssigner missionAssigner;
    private final RoomMissionReader roomMissionReader;
    private final RoomMissionWriter roomMissionWriter;
    private final MissionReader missionReader;
    private final RoomMemberReader roomMemberReader;
    private final Clock clock;

    public RoomMissionService(MissionAssigner missionAssigner, RoomMissionReader roomMissionReader,
                              RoomMissionWriter roomMissionWriter, MissionReader missionReader,
                              RoomMemberReader roomMemberReader, Clock clock) {
        this.missionAssigner = missionAssigner;
        this.roomMissionReader = roomMissionReader;
        this.roomMissionWriter = roomMissionWriter;
        this.missionReader = missionReader;
        this.roomMemberReader = roomMemberReader;
        this.clock = clock;
    }

    /**
     * 방 시작 시 미션 인스턴스를 일괄 배정한다(RoomStartedEvent 커밋 후 호출). room_id 당 1회 멱등 —
     * 이벤트 재발행에도 중복 배정하지 않는다. AFTER_COMMIT 리스너에서 호출되므로 REQUIRES_NEW로 새 트랜잭션을 연다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assignForRoom(Room room) {
        if (roomMissionReader.isAssigned(room.id())) {
            return;
        }
        List<RoomMission> assigned = missionAssigner.assign(room);
        if (!assigned.isEmpty()) {
            roomMissionWriter.saveAll(assigned);
        }
    }

    /** 지금 열려 있는 미션 카드(문구 포함). 방 멤버만 조회 가능. /today 미션 노출용. */
    public List<RoomMissionResponse> getOpenMissions(Long roomId, Long userId) {
        roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트(비멤버면 예외)
        List<RoomMission> open = roomMissionReader.getOpenMissions(roomId, LocalDateTime.now(clock));
        if (open.isEmpty()) {
            return List.of();
        }
        Map<Long, Mission> missionsById = missionReader.getActiveCandidates().stream()
                .collect(Collectors.toMap(Mission::id, Function.identity(), (a, b) -> a));
        return open.stream()
                .filter(rm -> missionsById.containsKey(rm.missionId())) // 비활성화된 미션 문구는 카드에서 제외
                .map(rm -> RoomMissionResponse.of(rm, missionsById.get(rm.missionId())))
                .toList();
    }
}
