package org.son.haruharu.batch.slotstartnotification;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.business.SlotStartNotifier;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SlotStartNotificationJobTaskletTest {

    @Mock SlotStartNotifier slotStartNotifier;

    SlotStartNotificationJobTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new SlotStartNotificationJobTasklet(slotStartNotifier);
    }

    private static StepContribution contributionWithBucket(String bucket) {
        return MetaDataInstanceFactory.createStepExecution(
                        new JobParametersBuilder().addString("bucket", bucket).toJobParameters())
                .createStepContribution();
    }

    @Test
    @DisplayName("런처가 정한 bucket 정각을 그대로 notifier 에 넘기고, 대상 수를 스텝 기록에 남긴 뒤 FINISHED 를 반환한다")
    void givenBucket_whenExecute_thenNotifiesWithSameSlotAtAndRecordsCount() throws Exception {
        LocalDateTime slotAt = LocalDateTime.of(2026, 9, 19, 23, 0);
        given(slotStartNotifier.notifyOpeningSlots(slotAt)).willReturn(3);
        StepContribution contribution = contributionWithBucket(slotAt.toString());

        RepeatStatus status = tasklet.execute(contribution, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        then(slotStartNotifier).should().notifyOpeningSlots(slotAt);
        assertThat(contribution.getWriteCount()).isEqualTo(3);
    }
}
