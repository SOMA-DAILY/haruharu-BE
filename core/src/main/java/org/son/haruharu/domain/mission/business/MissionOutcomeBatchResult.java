package org.son.haruharu.domain.mission.business;

/** outcome 확정 배치 1회 실행 요약. */
public record MissionOutcomeBatchResult(
        int examined,
        int confirmed,
        int skipped,
        int failed
) {
    public static MissionOutcomeBatchResult empty() {
        return new MissionOutcomeBatchResult(0, 0, 0, 0);
    }
}
