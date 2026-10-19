package org.son.haruharu.batch.roomreconcile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RoomReconcileJobLauncherTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 실제 적용값은 어노테이션 기본값이 아니라 yaml 값이므로 yaml 을 읽어 확인한다(SlotStartNotificationJobLauncherTest 와 같은 방식).
    private static final Pattern YAML_CRON = Pattern.compile(
            "cron:\\s*\\$\\{(SLOT_START_NOTIFICATION|CHAT_ADMIN_MESSAGE|ROOM_RECONCILE)_CRON:([^}]+)}");

    @Mock JobOperator jobOperator;
    @Mock Job roomReconcileJob;

    private RoomReconcileJobLauncher launcherAt(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL);
        return new RoomReconcileJobLauncher(jobOperator, roomReconcileJob, clock);
    }

    private String launchedBucket() throws Exception {
        ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);
        then(jobOperator).should().start(eq(roomReconcileJob), parameters.capture());
        return parameters.getValue().getString("bucket");
    }

    @Test
    @DisplayName("bucket 은 실행 시각을 5분 단위로 내림한 시각이다 — 날짜 bucket 이면 하루 첫 실행 뒤 전부 멱등 스킵된다")
    void givenMidInterval_whenLaunch_thenBucketIsFiveMinuteFloor() throws Exception {
        launcherAt(LocalDateTime.of(2026, 10, 2, 14, 7, 45)).launch();

        assertThat(launchedBucket()).isEqualTo("2026-10-02T14:05");
    }

    @Test
    @DisplayName("cron 발화 시각(5분 경계 + 45초)은 그 5분 경계 bucket 이 된다")
    void givenCronFireTime_whenLaunch_thenBucketIsThatBoundary() throws Exception {
        launcherAt(LocalDateTime.of(2026, 10, 2, 14, 10, 45)).launch();

        assertThat(launchedBucket()).isEqualTo("2026-10-02T14:10");
    }

    @Test
    @DisplayName("같은 bucket 이 이미 실행됐으면 예외를 밖으로 던지지 않고 스킵한다(멱등)")
    void givenAlreadyCompleted_whenLaunch_thenSwallows() throws Exception {
        given(jobOperator.start(any(Job.class), any(JobParameters.class)))
                .willThrow(new JobInstanceAlreadyCompleteException("already complete"));

        assertThatCode(() -> launcherAt(LocalDateTime.of(2026, 10, 2, 14, 5, 45)).launch())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("JobRepository 직렬화 충돌(동시 launch)도 스케줄러 스레드로 전파하지 않는다")
    void givenSerializationConflict_whenLaunch_thenSwallows() throws Exception {
        given(jobOperator.start(any(Job.class), any(JobParameters.class)))
                .willThrow(new CannotAcquireLockException("could not serialize access"));

        assertThatCode(() -> launcherAt(LocalDateTime.of(2026, 10, 2, 14, 5, 45)).launch())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("launch 실패도 스케줄러 스레드로 전파하지 않는다")
    void givenUnexpectedFailure_whenLaunch_thenSwallows() throws Exception {
        given(jobOperator.start(any(Job.class), any(JobParameters.class)))
                .willThrow(new IllegalStateException("boom"));

        assertThatCode(() -> launcherAt(LocalDateTime.of(2026, 10, 2, 14, 5, 45)).launch())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("@Scheduled 는 yaml 의 haruharu.room.reconcile.cron 을 읽는다 — 하드코딩하면 yaml·env 오버라이드가 무시된다")
    void givenLauncher_whenReadingScheduledCron_thenUsesYamlProperty() throws Exception {
        Scheduled scheduled = RoomReconcileJobLauncher.class.getMethod("launch").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).startsWith("${haruharu.room.reconcile.cron:");
    }

    @Test
    @DisplayName("yaml 기본 cron 은 유효한 표현식이고, 초 필드가 다른 배치·스케줄 잡과 겹치지 않는다")
    void givenYamlCrons_whenComparing_thenRoomReconcileIsValidAndAvoidsOtherJobs() throws Exception {
        Map<String, String> cronByJob = new HashMap<>();
        Matcher matcher = YAML_CRON.matcher(readApplicationYaml());
        while (matcher.find()) {
            cronByJob.put(matcher.group(1), matcher.group(2).trim());
        }

        assertThat(cronByJob).containsKeys(
                "SLOT_START_NOTIFICATION", "CHAT_ADMIN_MESSAGE", "ROOM_RECONCILE");
        String roomReconcile = cronByJob.get("ROOM_RECONCILE");
        assertThatCode(() -> CronExpression.parse(roomReconcile))
                .as("잘못된 cron 이면 @Scheduled 등록 단계에서 앱 부팅이 실패한다")
                .doesNotThrowAnyException();
        assertThat(CronExpression.parse(roomReconcile)
                .next(LocalDateTime.of(2026, 10, 2, 14, 5, 45)))
                .as("앱 폴백은 기존 Airflow와 같은 5분 주기의 45초에 실행된다")
                .isEqualTo(LocalDateTime.of(2026, 10, 2, 14, 10, 45));
        assertThat(secondsField(roomReconcile))
                .isNotEqualTo(secondsField(cronByJob.get("SLOT_START_NOTIFICATION")))
                .isNotEqualTo(secondsField(cronByJob.get("CHAT_ADMIN_MESSAGE")));
    }

    private static String secondsField(String cron) {
        return cron.trim().split("\\s+")[0];
    }

    private static String readApplicationYaml() throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null
                && !Files.isRegularFile(directory.resolve("app/src/main/resources/application.yaml"))) {
            directory = directory.getParent();
        }
        if (directory == null) {
            throw new IllegalStateException("application.yaml 을 찾지 못했다");
        }
        return Files.readString(directory.resolve("app/src/main/resources/application.yaml"), StandardCharsets.UTF_8);
    }
}
