package org.son.haruharu.batch.missionassignment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "haruharu.mission.assignment")
public record MissionAssignmentBatchProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("60") int leadMinutes,
        @DefaultValue("100") int batchSize
) {
    public MissionAssignmentBatchProperties {
        if (leadMinutes < 0 || batchSize <= 0) {
            throw new IllegalArgumentException("Mission assignment batch properties must be positive");
        }
    }
}
