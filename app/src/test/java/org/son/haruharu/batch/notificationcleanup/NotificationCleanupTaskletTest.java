package org.son.haruharu.batch.notificationcleanup;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.implement.NotificationHistoryWriter;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationCleanupTaskletTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock NotificationHistoryWriter notificationHistoryWriter;

    NotificationCleanupTasklet tasklet;

    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 6, 24, 4, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    @BeforeEach
    void setUp() {
        tasklet = new NotificationCleanupTasklet(notificationHistoryWriter, clock);
    }

    @Test
    @DisplayName("30일 지난 알림함을 삭제하고 삭제 건수를 스텝 기록에 남긴 뒤 FINISHED를 반환한다")
    void givenOldNotifications_whenExecute_thenDeletesOlderThan30DaysAndRecordsCount() throws Exception {
        // now=2026-06-24 → threshold = 05-25 04:00
        LocalDateTime expectedThreshold = LocalDateTime.of(2026, 5, 25, 4, 0);
        given(notificationHistoryWriter.deleteOlderThan(expectedThreshold)).willReturn(7);

        StepContribution contribution = MetaDataInstanceFactory.createStepExecution().createStepContribution();
        RepeatStatus status = tasklet.execute(contribution, null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(notificationHistoryWriter).deleteOlderThan(expectedThreshold);
        assertThat(contribution.getWriteCount()).isEqualTo(7); // 관측: 삭제 건수 기록
    }
}
