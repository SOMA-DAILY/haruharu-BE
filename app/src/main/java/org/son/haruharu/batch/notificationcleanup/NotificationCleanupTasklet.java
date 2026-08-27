package org.son.haruharu.batch.notificationcleanup;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.notification.implement.NotificationHistoryWriter;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * 알림함 보관 정리 잡의 실제 작업(#357). 30일 지난 notification_history 를 삭제한다.
 * <p>
 * 비즈니스 판단 없는 단일 삭제라 <b>Tasklet</b>(한 덩어리 실행)로 감쌌다 — chunk(read/process/write) 가 필요 없다.
 * 두 번 돌아도 이미 지워져 0건이라 멱등하다(잡 단위 멱등은 런처의 시간 버킷 파라미터가 별도로 보장한다).
 */
@Component
public class NotificationCleanupTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupTasklet.class);
    private static final int RETENTION_DAYS = 30;

    private final NotificationHistoryWriter notificationHistoryWriter;
    private final Clock clock;

    public NotificationCleanupTasklet(NotificationHistoryWriter notificationHistoryWriter, Clock clock) {
        this.notificationHistoryWriter = notificationHistoryWriter;
        this.clock = clock;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDateTime threshold = LocalDateTime.now(clock).minusDays(RETENTION_DAYS);
        int deleted = notificationHistoryWriter.deleteOlderThan(threshold);
        contribution.incrementWriteCount(deleted); // BATCH_STEP_EXECUTION 에 삭제 건수 기록(관측)
        log.info("알림함 정리 완료: {}건 삭제 (threshold={})", deleted, threshold);
        return RepeatStatus.FINISHED;
    }
}
