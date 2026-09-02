package org.son.haruharu.domain.mission.implement;

import java.math.BigDecimal;

/**
 * 배정 시점의 방 상태. <b>기록 전용이며 Rule V1 의 점수 계산에는 쓰지 않는다.</b>
 * <p>
 * 두 가지 이유다. 첫째, 임계값을 정할 데이터가 아직 없다. 둘째, 방 상태가 선택에 영향을 주지 않는 동안에는
 * {@code 방 상태 → 선택 → 성과} 교란이 없어 자연 실험 조건이 성립한다. 이 구간의 데이터가 Rule V2 의
 * 임계값을 정하는 근거가 된다. 지금 기록하지 않으면 소급 복원이 불가능하다.
 */
public record RoomStateSnapshot(
        int memberCount,
        int closedMissionCount,
        int activatedMemberCount,
        BigDecimal averageActivationCoverage,
        BigDecimal lastActivationCoverage
) {
    public static RoomStateSnapshot empty(int memberCount) {
        return new RoomStateSnapshot(memberCount, 0, 0, null, null);
    }
}
