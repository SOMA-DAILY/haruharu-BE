package org.son.haruharu.infrastructure.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.config.ChatFeatureConfig;
import org.son.haruharu.domain.mission.chat.business.MissionDispatchService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MissionDispatchSchedulerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ChatFeatureConfig.class, MissionDispatchScheduler.class)
            .withBean(MissionDispatchService.class, () -> mock(MissionDispatchService.class));

    @Test
    @DisplayName("시스템 유저 설정 없이도 발송 플래그가 켜지면 잡을 기동한다")
    void givenEnabledWithoutSystemUser_thenStartupSucceeds() {
        runner.withPropertyValues("haruharu.chat.admin-message.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MissionDispatchScheduler.class);
                });
    }

    @Test
    @DisplayName("플래그 ON이면 잡을 등록하고 OFF이면 등록하지 않는다")
    void givenFlags_thenBeanPresence() {
        runner.withPropertyValues("haruharu.chat.admin-message.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(MissionDispatchScheduler.class));
        runner.withPropertyValues("haruharu.chat.admin-message.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MissionDispatchScheduler.class);
                });
        runner.run(context -> assertThat(context).doesNotHaveBean(MissionDispatchScheduler.class));
    }
}
