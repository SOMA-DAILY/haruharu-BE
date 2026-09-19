package org.son.haruharu.batch.slotstartnotification;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SlotStartNotificationJobLauncherTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 실제 적용값은 어노테이션 기본값이 아니라 yaml 값이므로 yaml 을 읽어 확인한다(MissionBatchCronScheduleTest 와 같은 방식).
    private static final Pattern YAML_CRON = Pattern.compile(
            "cron:\\s*\\$\\{(MISSION_ASSIGNMENT|MISSION_OUTCOME|SLOT_START_NOTIFICATION)_CRON:([^}]+)}");

    @Mock JobOperator jobOperator;
    @Mock Job slotStartNotificationJob;

    private SlotStartNotificationJobLauncher launcherAt(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL);
        return new SlotStartNotificationJobLauncher(jobOperator, slotStartNotificationJob, clock);
    }

    private String launchedBucket() throws Exception {
        ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);
        then(jobOperator).should().start(eq(slotStartNotificationJob), parameters.capture());
        return parameters.getValue().getString("bucket");
    }

    @Test
    @DisplayName("정각 직후(cron 15초)에 실행되면 bucket 은 그 정각이다")
    void givenJustAfterHour_whenLaunch_thenBucketIsThatHour() throws Exception {
        launcherAt(LocalDateTime.of(2026, 9, 19, 23, 0, 15)).launch();

        assertThat(launchedBucket()).isEqualTo("2026-09-19T23:00");
    }

    @Test
    @DisplayName("정각 직전에 발화해도(#421 실측 xx:59:59.988) 절삭이 아니라 반올림이라 다음 정각 bucket 이 된다")
    void givenJustBeforeHour_whenLaunch_thenBucketRoundsUpToNextHour() throws Exception {
        launcherAt(LocalDateTime.of(2026, 9, 19, 22, 59, 59, 988_000_000)).launch();

        assertThat(launchedBucket()).isEqualTo("2026-09-19T23:00");
    }

    @Test
    @DisplayName("같은 bucket 이 이미 실행됐으면 예외를 밖으로 던지지 않고 스킵한다(멱등)")
    void givenAlreadyCompleted_whenLaunch_thenSwallows() throws Exception {
        given(jobOperator.start(any(Job.class), any(JobParameters.class)))
                .willThrow(new JobInstanceAlreadyCompleteException("already complete"));

        assertThatCode(() -> launcherAt(LocalDateTime.of(2026, 9, 19, 23, 0, 15)).launch())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("launch 실패도 스케줄러 스레드로 전파하지 않는다")
    void givenUnexpectedFailure_whenLaunch_thenSwallows() throws Exception {
        given(jobOperator.start(any(Job.class), any(JobParameters.class)))
                .willThrow(new IllegalStateException("boom"));

        assertThatCode(() -> launcherAt(LocalDateTime.of(2026, 9, 19, 23, 0, 15)).launch())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("yaml 기본 cron 의 초 필드는 미션 배정·outcome 잡과 겹치지 않는다 — 겹치면 JobRepository 직렬화 충돌로 한쪽이 실행되지 않는다")
    void givenYamlCrons_whenComparingSecondsField_thenSlotStartAvoidsMissionJobs() throws Exception {
        Map<String, String> cronByJob = new HashMap<>();
        Matcher matcher = YAML_CRON.matcher(readApplicationYaml());
        while (matcher.find()) {
            cronByJob.put(matcher.group(1), matcher.group(2).trim());
        }

        assertThat(cronByJob).containsKeys("MISSION_ASSIGNMENT", "MISSION_OUTCOME", "SLOT_START_NOTIFICATION");
        assertThat(secondsField(cronByJob.get("SLOT_START_NOTIFICATION")))
                .isNotEqualTo(secondsField(cronByJob.get("MISSION_ASSIGNMENT")))
                .isNotEqualTo(secondsField(cronByJob.get("MISSION_OUTCOME")));
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
