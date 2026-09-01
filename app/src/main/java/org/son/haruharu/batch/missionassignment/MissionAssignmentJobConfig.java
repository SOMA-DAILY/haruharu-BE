package org.son.haruharu.batch.missionassignment;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableConfigurationProperties(MissionAssignmentBatchProperties.class)
public class MissionAssignmentJobConfig {

    public static final String JOB_NAME = "missionAssignmentJob";

    @Bean
    public Job missionAssignmentJob(JobRepository jobRepository, Step missionAssignmentStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(missionAssignmentStep)
                .build();
    }

    @Bean
    public Step missionAssignmentStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            MissionAssignmentTasklet tasklet
    ) {
        return new StepBuilder("missionAssignmentStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
