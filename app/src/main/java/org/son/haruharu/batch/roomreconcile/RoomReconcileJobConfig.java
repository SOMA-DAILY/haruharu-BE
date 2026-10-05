package org.son.haruharu.batch.roomreconcile;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoomReconcileJobConfig {

    public static final String JOB_NAME = "roomReconcileJob";

    @Bean
    public Job roomReconcileJob(JobRepository jobRepository, Step roomReconcileStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(roomReconcileStep)
                .build();
    }

    @Bean
    public Step roomReconcileStep(JobRepository jobRepository,
                                    RoomReconcileJobTasklet roomReconcileJobTasklet) {
        return new StepBuilder("roomReconcileStep", jobRepository)
                .tasklet(roomReconcileJobTasklet, new ResourcelessTransactionManager())
                .build();
    }
}
