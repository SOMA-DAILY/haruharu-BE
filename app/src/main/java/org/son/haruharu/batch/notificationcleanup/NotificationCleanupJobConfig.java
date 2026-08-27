package org.son.haruharu.batch.notificationcleanup;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 알림함 정리 배치 잡 정의(#357). 단일 Tasklet 스텝의 Job.
 * <p>
 * JobRepository·PlatformTransactionManager 는 Spring Boot 가 DataSource 로부터 자동 구성한다.
 * 잡 이름("notificationCleanupJob")은 JobRepository 의 JobInstance 식별에 쓰인다 — 런처의 시간 버킷 파라미터와 합쳐
 * (job_name, job_key) 유니크로 "같은 버킷 한 번만 실행"이 강제된다.
 */
@Configuration
public class NotificationCleanupJobConfig {

    public static final String JOB_NAME = "notificationCleanupJob";

    @Bean
    public Job notificationCleanupJob(JobRepository jobRepository, Step notificationCleanupStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(notificationCleanupStep)
                .build();
    }

    @Bean
    public Step notificationCleanupStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                        NotificationCleanupTasklet notificationCleanupTasklet) {
        return new StepBuilder("notificationCleanupStep", jobRepository)
                .tasklet(notificationCleanupTasklet, transactionManager)
                .build();
    }
}
