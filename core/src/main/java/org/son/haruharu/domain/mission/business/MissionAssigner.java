package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.room.implement.Room;

import java.util.List;

/**
 * 방에 어떤 미션을 몇 개, 언제 배정할지 결정하는 정책. "몇 개를 언제"를 여기서만 정해 확장 지점을 격리한다.
 * <p>
 * 기본 구현은 {@code FixedTwicePerDayAssigner}(매일 오전/오후 2개, starts_at 상대). 나중에 방 활성도에 따라
 * 개수를 조절하려면 이 인터페이스의 다른 구현체(예: ActivityBasedAssigner)로 갈아끼우면 되고,
 * room_missions 스키마·조회·트리거는 바뀌지 않는다.
 */
public interface MissionAssigner {

    /** 방에 배정할 미션 인스턴스 목록(미저장). opensAt은 절대 시각. */
    List<RoomMission> assign(Room room);
}
