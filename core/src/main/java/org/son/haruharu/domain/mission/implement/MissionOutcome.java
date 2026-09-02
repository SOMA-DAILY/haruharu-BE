package org.son.haruharu.domain.mission.implement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 미션 시간창이 닫힌 뒤 확정하는 불변 성과 스냅샷.
 * <p>
 * Primary metric 은 {@code roomActivationCoverage} — 시간창 동안 영상 업로드 또는 그룹채팅을 한
 * 고유 대상 멤버 수를 슬롯 오픈 시점의 대상 멤버 수로 나눈 값이다. 미션 내용의 수행 여부는 판정하지 않는다.
 * <p>
 * 집계식이 바뀌면 기존 행을 덮어쓰지 않고 새 {@code metricSchemaVersion} 으로 행을 추가한다.
 * 과거 정책 비교의 기준선이 사후에 변형되면 안 되기 때문이다.
 */
public record MissionOutcome(
        Long id,
        Long roomMissionId,
        String metricSchemaVersion,
        int targetMemberCount,
        int impressedMemberCount,
        int activatedMemberCount,
        int expectedChannelActivatedCount,
        int crossChannelActivatedCount,
        int dismissedMemberCount,
        int expiredMemberCount,
        BigDecimal roomActivationCoverage,
        BigDecimal exposureReach,
        LocalDateTime closesAt,
        LocalDateTime calculatedAt,
        LocalDateTime createdAt
) {
    /** 현재 집계식 버전. 식이 바뀌면 새 상수를 만들고 과거 행은 그대로 둔다. */
    public static final String METRIC_SCHEMA_V1 = "MISSION_OUTCOME_V1";

    /** 비율 컬럼 스케일. numeric(5,4) 로 0.0000 ~ 1.0000 을 담는다. */
    public static final int RATIO_SCALE = 4;

    public static MissionOutcome confirmed(
            Long roomMissionId,
            String metricSchemaVersion,
            MissionMemberStateSummary summary,
            LocalDateTime closesAt,
            LocalDateTime calculatedAt
    ) {
        return new MissionOutcome(
                null,
                roomMissionId,
                metricSchemaVersion,
                summary.targetMemberCount(),
                summary.impressedMemberCount(),
                summary.activatedMemberCount(),
                summary.expectedChannelActivatedCount(),
                summary.crossChannelActivatedCount(),
                summary.dismissedMemberCount(),
                summary.expiredMemberCount(),
                ratio(summary.activatedMemberCount(), summary.targetMemberCount()),
                ratio(summary.impressedMemberCount(), summary.targetMemberCount()),
                closesAt,
                calculatedAt,
                null);
    }

    /**
     * 분모가 0이면 0을 반환한다. 대상 멤버가 없는 미션은 실패가 아니라 "활성화 대상이 없었음"이므로
     * 집계를 중단하지 않고 0으로 남긴다.
     */
    public static BigDecimal ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
