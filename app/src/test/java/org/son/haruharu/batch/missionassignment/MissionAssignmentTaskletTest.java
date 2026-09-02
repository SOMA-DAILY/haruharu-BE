package org.son.haruharu.batch.missionassignment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.business.MissionAssignmentBatchResult;
import org.son.haruharu.domain.mission.business.MissionAssignmentBatchService;
import org.son.haruharu.domain.mission.business.MissionMemberStateInitializer;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MissionAssignmentTaskletTest {

    @Mock MissionAssignmentBatchService batchService;
    @Mock MissionMemberStateInitializer stateInitializer;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-02T02:30:00Z"), ZoneId.of("Asia/Seoul"));
    MissionAssignmentTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new MissionAssignmentTasklet(
                batchService, stateInitializer,
                new MissionAssignmentBatchProperties(true, 60, 100), clock);
    }

    @Test
    @DisplayName("JIT 결과의 조회·배정 건수를 Spring Batch step contribution에 기록한다")
    void givenDueSlots_whenExecute_thenRecordsBatchCounts() throws Exception {
        given(batchService.processDueSlots(
                java.time.LocalDateTime.of(2026, 9, 2, 11, 30), Duration.ofMinutes(60), 100))
                .willReturn(new MissionAssignmentBatchResult(3, 2, 1, 0, 1, 0));
        given(stateInitializer.initializeOpenedTargets(
                java.time.LocalDateTime.of(2026, 9, 2, 11, 30), 100)).willReturn(6);
        StepContribution contribution = MetaDataInstanceFactory.createStepExecution().createStepContribution();

        RepeatStatus status = tasklet.execute(contribution, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        assertThat(contribution.getReadCount()).isEqualTo(3);
        assertThat(contribution.getWriteCount()).isEqualTo(2);
        then(batchService).should().processDueSlots(
                java.time.LocalDateTime.of(2026, 9, 2, 11, 30), Duration.ofMinutes(60), 100);
        then(stateInitializer).should().initializeOpenedTargets(
                java.time.LocalDateTime.of(2026, 9, 2, 11, 30), 100);
    }
}
