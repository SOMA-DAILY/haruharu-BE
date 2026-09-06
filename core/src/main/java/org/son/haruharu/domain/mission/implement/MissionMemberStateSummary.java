package org.son.haruharu.domain.mission.implement;

/**
 * 한 미션 인스턴스의 멤버 상태를 집계한 원시 카운트. outcome 확정 시점에 한 번 조회한다.
 * <p>
 * 활동(activated)과 카드 해소(resolution)는 다른 개념이다. 노출 없이 활동한 멤버는 활동으로 세지만
 * 카드는 해소되지 않는다. Primary metric 인 방 활성화는 노출 여부와 무관한 활동 기준이다.
 */
public record MissionMemberStateSummary(
        int targetMemberCount,
        int impressedMemberCount,
        int activatedMemberCount,
        int expectedChannelActivatedCount,
        int crossChannelActivatedCount,
        int expiredMemberCount
) {
    public static MissionMemberStateSummary empty() {
        return new MissionMemberStateSummary(0, 0, 0, 0, 0, 0);
    }
}
