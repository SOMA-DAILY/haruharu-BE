package org.son.haruharu.domain.mission.business;

public record MissionAssignmentBatchResult(
        int examined,
        int assigned,
        int degraded,
        int retrying,
        int failed,
        int skipped
) {
}
