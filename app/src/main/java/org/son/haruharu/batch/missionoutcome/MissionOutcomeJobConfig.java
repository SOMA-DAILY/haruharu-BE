package org.son.haruharu.batch.missionoutcome;

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
@EnableConfigurationProperties(MissionOutcomeBatchProperties.class)
public class MissionOutcomeJobConfig {

    public static final String JOB_NAME = "missionOutcomeJob";

    @Bean
    public Job missionOutcomeJob(JobRepository jobRepository, Step missionOutcomeStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(missionOutcomeStep)
                .build();
    }

    @Bean
    public Step missionOutcomeStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            MissionOutcomeTasklet tasklet
    ) {
        return new StepBuilder("missionOutcomeStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
