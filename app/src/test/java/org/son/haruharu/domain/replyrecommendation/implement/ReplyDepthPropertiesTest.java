package org.son.haruharu.domain.replyrecommendation.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.son.haruharu.config.ReplyDepthConfig;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Depth 라벨 설정 검증. 0 이하 수치는 라벨을 오염시키므로 앱 시작을 막는다. */
class ReplyDepthPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(ReplyDepthConfig.class);

    @Test
    @DisplayName("기본값이면 시작된다")
    void givenDefaults_whenStart_thenBound() {
        // given // when // then
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            ReplyDepthProperties properties = context.getBean(ReplyDepthProperties.class);
            assertThat(properties.observationHours()).isEqualTo(24);
            assertThat(properties.maxAttempts()).isEqualTo(3);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"observation-hours", "batch-size", "max-depth", "max-attempts"})
    @DisplayName("수치 설정이 0 이면 시작에 실패한다")
    void givenZero_whenStart_thenFails(String key) {
        // given
        String property = "haruharu.reply-recommendation.depth." + key + "=0";

        // when // then
        runner.withPropertyValues(property)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("관찰 기간이 음수면 시작에 실패한다")
    void givenNegativeObservationHours_whenStart_thenFails() {
        // given // when // then
        runner.withPropertyValues("haruharu.reply-recommendation.depth.observation-hours=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("관찰 기간 1 시간은 허용된다")
    void givenOneHour_whenStart_thenBound() {
        // given // when // then
        runner.withPropertyValues("haruharu.reply-recommendation.depth.observation-hours=1")
                .run(context -> assertThat(context.getBean(ReplyDepthProperties.class).observationHours())
                        .isEqualTo(1));
    }

    @Test
    @DisplayName("효과 라벨 창은 기본 30분이고, 관찰 기간보다 길면 시작에 실패한다")
    void givenResponseWindow_whenStart_thenDefaultOrFailsBeyondObservation() {
        runner.run(context -> assertThat(context.getBean(ReplyDepthProperties.class).responseWindowMinutes())
                .isEqualTo(30));
        runner.withPropertyValues("haruharu.reply-recommendation.depth.observation-hours=1",
                        "haruharu.reply-recommendation.depth.response-window-minutes=60")
                .run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues("haruharu.reply-recommendation.depth.observation-hours=1",
                        "haruharu.reply-recommendation.depth.response-window-minutes=61")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("haruharu.reply-recommendation.depth.response-window-minutes=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
