package org.son.haruharu.batch.missionoutcome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.mission.business.MissionOutcomeBatchResult;
import org.son.haruharu.domain.mission.business.MissionOutcomeBatchService;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class MissionOutcomeTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(MissionOutcomeTasklet.class);

    private final MissionOutcomeBatchService batchService;
    private final MissionOutcomeBatchProperties properties;
    private final Clock clock;

    public MissionOutcomeTasklet(
            MissionOutcomeBatchService batchService,
            MissionOutcomeBatchProperties properties,
            Clock clock
    ) {
        this.batchService = batchService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDateTime now = LocalDateTime.now(clock);
        MissionOutcomeBatchResult result = batchService.processClosedMissions(
                now, Duration.ofMinutes(properties.settleMinutes()), properties.batchSize());
        for (int index = 0; index < result.examined(); index++) {
            contribution.incrementReadCount();
        }
        contribution.incrementWriteCount(result.confirmed());
        log.atInfo()
                .addKeyValue("event", "MISSION_OUTCOME_BATCH_COMPLETED")
                .addKeyValue("examined", result.examined())
                .addKeyValue("confirmed", result.confirmed())
                .addKeyValue("skipped", result.skipped())
                .addKeyValue("failed", result.failed())
                .log("미션 outcome 확정 배치 완료");
        return RepeatStatus.FINISHED;
    }
}
