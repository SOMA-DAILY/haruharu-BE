package org.son.haruharu.batch.slotstartnotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.notification.business.SlotStartNotifier;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SlotStartNotificationJobTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(SlotStartNotificationJobTasklet.class);

    private final SlotStartNotifier slotStartNotifier;

    public SlotStartNotificationJobTasklet(SlotStartNotifier slotStartNotifier) {
        this.slotStartNotifier  = slotStartNotifier;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // 현재 잡의 실행 시간
        LocalDateTime slotAt = LocalDateTime.parse(
                contribution.getStepExecution().getJobParameters().getString("bucket"));
        int notified = slotStartNotifier.notifyOpeningSlots(slotAt);
        contribution.incrementWriteCount(notified);
        log.info("슬롯 시작 알림 완료: slotAt={} 대상={}명", slotAt, notified);
        return RepeatStatus.FINISHED;
    }


}
