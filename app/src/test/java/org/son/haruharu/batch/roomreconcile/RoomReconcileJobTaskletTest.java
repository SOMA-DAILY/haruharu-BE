package org.son.haruharu.batch.roomreconcile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.RoomStatusReconciler;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RoomReconcileJobTaskletTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock RoomStatusReconciler roomStatusReconciler;

    private static StepContribution contributionWithBucket(String bucket) {
        return MetaDataInstanceFactory.createStepExecution(
                        new JobParametersBuilder().addString("bucket", bucket).toJobParameters())
                .createStepContribution();
    }

    @Test
    @DisplayName("bucket 이 아니라 실제 실행 시각으로 reconcile 하고, 전이 수를 스텝 기록에 남긴 뒤 FINISHED 를 반환한다")
    void givenRunTime_whenExecute_thenReconcilesWithActualNowAndRecordsCount() throws Exception {
        // bucket(14:05)으로 판정하면 result_at=14:05:30 인 방이 이번 실행에서 빠져 resolve 가 다음 틱까지 밀린다.
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 14, 5, 45);
        Clock clock = Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL);
        RoomReconcileJobTasklet tasklet = new RoomReconcileJobTasklet(roomStatusReconciler, clock);
        given(roomStatusReconciler.reconcile(now)).willReturn(2);
        StepContribution contribution = contributionWithBucket("2026-10-02T14:05");

        RepeatStatus status = tasklet.execute(contribution, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        then(roomStatusReconciler).should().reconcile(now);
        assertThat(contribution.getWriteCount()).isEqualTo(2);
    }
}
