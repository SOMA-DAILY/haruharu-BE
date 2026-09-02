package org.son.haruharu.batch.missionassignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.mission.business.MissionAssignmentBatchResult;
import org.son.haruharu.domain.mission.business.MissionAssignmentBatchService;
import org.son.haruharu.domain.mission.business.MissionMemberStateInitializer;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class MissionAssignmentTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(MissionAssignmentTasklet.class);

    private final MissionAssignmentBatchService batchService;
    private final MissionMemberStateInitializer stateInitializer;
    private final MissionAssignmentBatchProperties properties;
    private final Clock clock;

    public MissionAssignmentTasklet(
            MissionAssignmentBatchService batchService,
            MissionMemberStateInitializer stateInitializer,
            MissionAssignmentBatchProperties properties,
            Clock clock
    ) {
        this.batchService = batchService;
        this.stateInitializer = stateInitializer;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDateTime now = LocalDateTime.now(clock);
        MissionAssignmentBatchResult result = batchService.processDueSlots(
                now, Duration.ofMinutes(properties.leadMinutes()), properties.batchSize());
        int initializedTargets = stateInitializer.initializeOpenedTargets(now, properties.batchSize());
        for (int index = 0; index < result.examined(); index++) {
            contribution.incrementReadCount();
        }
        contribution.incrementWriteCount(result.assigned());
        log.atInfo()
                .addKeyValue("event", "MISSION_ASSIGNMENT_BATCH_COMPLETED")
                .addKeyValue("examined", result.examined())
                .addKeyValue("assigned", result.assigned())
                .addKeyValue("initializedTargets", initializedTargets)
                .addKeyValue("degraded", result.degraded())
                .addKeyValue("retrying", result.retrying())
                .addKeyValue("failed", result.failed())
                .addKeyValue("skipped", result.skipped())
                .log("미션 JIT 배정 배치 완료");
        return RepeatStatus.FINISHED;
    }
}
