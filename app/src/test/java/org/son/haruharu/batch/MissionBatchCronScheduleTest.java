package org.son.haruharu.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 미션 배정·outcome 두 배치 잡의 기본 cron 이 같은 초에 겹치지 않는지 지킨다.
 * <p>
 * Spring Batch 의 JobRepository 는 메타데이터를 SERIALIZABLE 로 기록한다. 두 잡이 같은 초에 launch 하면
 * 직렬화 충돌(CannotAcquireLockException)로 한쪽이 <b>아예 실행되지 못한다</b>. 그런데 잡이 안 도는 것
 * 자체는 테스트로 드러나지 않고 로그에만 남아서, 되돌아가도 조용히 넘어간다. 그래서 여기서 못 박는다.
 */
class MissionBatchCronScheduleTest {

    private static final Pattern CRON = Pattern.compile(
            "cron:\\s*\\$\\{MISSION_(ASSIGNMENT|OUTCOME)_CRON:([^}]+)}");

    @Test
    @DisplayName("배정 잡과 outcome 잡의 기본 cron 은 같은 초에 실행되지 않는다")
    void givenBothMissionJobs_whenComparingDefaultCron_thenTheyDoNotCollide() {
        String yaml = readApplicationYaml();

        Matcher matcher = CRON.matcher(yaml);
        String assignment = null;
        String outcome = null;
        while (matcher.find()) {
            if ("ASSIGNMENT".equals(matcher.group(1))) {
                assignment = matcher.group(2).trim();
            } else {
                outcome = matcher.group(2).trim();
            }
        }

        assertThat(assignment).as("assignment cron 기본값을 찾지 못했다").isNotNull();
        assertThat(outcome).as("outcome cron 기본값을 찾지 못했다").isNotNull();
        assertThat(outcome)
                .as("두 배치가 같은 초에 launch 하면 JobRepository 직렬화 충돌로 한쪽이 실행되지 않는다")
                .isNotEqualTo(assignment);
        assertThat(secondsField(outcome))
                .as("초 필드가 달라야 실제로 실행 시점이 어긋난다")
                .isNotEqualTo(secondsField(assignment));
    }

    private static String secondsField(String cron) {
        return cron.trim().split("\\s+")[0];
    }

    private static String readApplicationYaml() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null
                && !Files.isRegularFile(directory.resolve("app/src/main/resources/application.yaml"))) {
            directory = directory.getParent();
        }
        if (directory == null) {
            throw new IllegalStateException("application.yaml 을 찾지 못했다");
        }
        try {
            return Files.readString(
                    directory.resolve("app/src/main/resources/application.yaml"), StandardCharsets.UTF_8);
        } catch (IOException readFailure) {
            throw new UncheckedIOException(readFailure);
        }
    }
}
