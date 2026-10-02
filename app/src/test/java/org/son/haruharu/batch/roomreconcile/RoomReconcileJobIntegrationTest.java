package org.son.haruharu.batch.roomreconcile;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 방 status reconcile 배치 잡 end-to-end(#466). 실제 JobOperator/JDBC JobRepository 로 잡을 돌려
 * 저장 status 가 전이되는지, 같은 bucket 재실행이 거부되는지(멱등) 검증한다.
 * 전이·resolve 세부 규칙은 RoomStatusReconcileIntegrationTest·FinalSelectionResolveIntegrationTest 가 다룬다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RoomReconcileJobIntegrationTest {

    @Autowired JobOperator jobOperator;
    @Autowired Job roomReconcileJob;
    @Autowired RoomWriter roomWriter;
    @Autowired RoomReader roomReader;
    @Autowired ApplicationContext applicationContext;

    private static JobParameters bucket(String value) {
        return new JobParametersBuilder().addString("bucket", value).toJobParameters();
    }

    @Test
    @DisplayName("잡을 실행하면 시작 시각이 지난 PENDING 방이 ACTIVE 로 전이되고 잡은 COMPLETED 로 끝난다")
    void givenStartedPendingRoom_whenRunJob_thenRoomBecomesActive() throws Exception {
        LocalDateTime startsAt = LocalDateTime.now().minusHours(1);
        Room saved = roomWriter.save(Room.createScheduled(
                startsAt, startsAt.plusDays(2).withHour(18), startsAt.plusDays(2).withHour(21),
                startsAt.plusDays(2).withHour(22), startsAt.plusDays(3).withHour(9), 3));

        JobExecution execution = jobOperator.start(roomReconcileJob, bucket("it-transition"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(roomReader.findById(saved.id()).orElseThrow().status()).isEqualTo(RoomStatus.ACTIVE);
    }

    @Test
    @DisplayName("같은 bucket 으로 다시 실행하면 JobInstanceAlreadyComplete 로 거부된다(멱등·단일 실행)")
    void givenSameBucket_whenRunTwice_thenSecondRejected() throws Exception {
        JobExecution first = jobOperator.start(roomReconcileJob, bucket("2026-10-02T14:05"));
        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        assertThatThrownBy(() -> jobOperator.start(roomReconcileJob, bucket("2026-10-02T14:05")))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }

    @Test
    @DisplayName("enabled=false(test 프로필)면 cron 런처 빈은 없고 Job 빈은 남는다 — 내부 배치 API 로는 계속 실행할 수 있다")
    void givenLauncherDisabled_whenContextLoads_thenNoLauncherButJobRemains() {
        assertThat(applicationContext.getBeanNamesForType(RoomReconcileJobLauncher.class)).isEmpty();
        assertThat(roomReconcileJob.getName()).isEqualTo(RoomReconcileJobConfig.JOB_NAME);
    }
}
