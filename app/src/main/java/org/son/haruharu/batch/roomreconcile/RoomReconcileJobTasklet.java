package org.son.haruharu.batch.roomreconcile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.room.business.RoomStatusReconciler;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class RoomReconcileJobTasklet implements Tasklet {
    private static final Logger logger = LoggerFactory.getLogger(RoomReconcileJobTasklet.class);

    private final RoomStatusReconciler roomStatusReconciler;
    private final Clock clock;

    public RoomReconcileJobTasklet(RoomStatusReconciler roomStatusReconciler, Clock clock) {
        this.roomStatusReconciler = roomStatusReconciler;
        this.clock = clock;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int changed = roomStatusReconciler.reconcile(LocalDateTime.now(clock));
        contribution.incrementWriteCount(changed);
        logger.info("방 status 전이 완료: {}개 방", changed);
        return RepeatStatus.FINISHED;
    }
}
