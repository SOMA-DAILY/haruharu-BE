package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;

/**
 * 방별 미션 사용 여부(#449). 어드민이 방을 만들 때 정하며, 꺼진 방은 미션 슬롯을 만들지 않고
 * 영상·그룹채팅 활동도 미션으로 기록하지 않는다. room 도메인은 이 설정을 알지 않는다.
 */
public record RoomMissionSetting(
        Long id,
        Long roomId,
        boolean missionEnabled,
        LocalDateTime createdAt
) {
    public static RoomMissionSetting of(Long roomId, boolean missionEnabled) {
        return new RoomMissionSetting(null, roomId, missionEnabled, null);
    }
}
