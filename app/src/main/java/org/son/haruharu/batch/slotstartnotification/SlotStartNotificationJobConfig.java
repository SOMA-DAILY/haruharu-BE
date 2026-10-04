package org.son.haruharu.batch.slotstartnotification;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlotStartNotificationJobConfig {

    public static final String JOB_NAME =  "slotStartNotificationJob";

    @Bean
    public Job slotStartNotificationJob(JobRepository jobRepository, Step slotStartNotificationStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(slotStartNotificationStep)
                .build();
    }

    @Bean
    public Step slotStartNotificationStep(JobRepository jobRepository, SlotStartNotificationJobTasklet slotStartNotificationTasklet) {
        // 현재 작업은 FCM(외부 IO) 중심이기에 best-effort 발송이다. 그렇기에 DB 트랜잭션으로 감싸지 않는다.
        // 감싸면 발송 내내 커넥션을 잡고, 토큰 삭제 실패가 rollback-only 로 번져 잡 FAILED → 재시작 시 중복 발송된다.
        return new StepBuilder("slotStartNotificationStep", jobRepository)
                .tasklet(slotStartNotificationTasklet, new ResourcelessTransactionManager())
                .build();
    }
}
