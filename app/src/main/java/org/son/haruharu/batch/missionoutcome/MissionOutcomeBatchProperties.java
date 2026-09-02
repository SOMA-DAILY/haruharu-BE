package org.son.haruharu.batch.missionoutcome;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * settleMinutes 는 시간창이 닫힌 뒤 집계까지 두는 여유다. 활동 이벤트가 AFTER_COMMIT 으로 발행되므로
 * 마감 직전 활동이 늦게 반영될 수 있다. 판정 기준은 도착 시각이 아니라 occurredAt 이며 그 필터는
 * 활동 기록 시점에 이미 적용되므로, 이 여유는 지연 도착분을 놓치지 않기 위한 것이다.
 */
@ConfigurationProperties(prefix = "haruharu.mission.outcome")
public record MissionOutcomeBatchProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5") int settleMinutes,
        @DefaultValue("100") int batchSize
) {
    public MissionOutcomeBatchProperties {
        if (settleMinutes < 0 || batchSize <= 0) {
            throw new IllegalArgumentException("Mission outcome batch properties must be positive");
        }
    }
}
