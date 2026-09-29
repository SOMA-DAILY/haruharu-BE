package org.son.haruharu.infrastructure.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.config.ChatFeatureConfig;
import org.son.haruharu.domain.roomschedule.business.RoomScheduleDispatchService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RoomScheduleDispatchSchedulerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ChatFeatureConfig.class, RoomScheduleDispatchScheduler.class)
            .withBean(RoomScheduleDispatchService.class, () -> mock(RoomScheduleDispatchService.class));

    @Test
    @DisplayName("발송 플래그 ON 인데 시스템 유저 id 가 없으면 기동 실패(메시지에 설정 키)")
    void givenEnabledWithoutSystemUser_thenStartupFails() {
        runner.withPropertyValues("haruharu.chat.admin-message.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("CHAT_SYSTEM_USER_ID");
                });
    }

    @Test
    @DisplayName("플래그 ON + 시스템 유저 id 있으면 잡 빈이 뜨고, OFF 면 id 없어도 기동(잡 빈 없음)")
    void givenFlags_thenBeanPresence() {
        runner.withPropertyValues("haruharu.chat.admin-message.enabled=true", "haruharu.chat.system-user-id=7")
                .run(context -> assertThat(context).hasSingleBean(RoomScheduleDispatchScheduler.class));
        runner.withPropertyValues("haruharu.chat.admin-message.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RoomScheduleDispatchScheduler.class);
                });
        runner.run(context -> assertThat(context).doesNotHaveBean(RoomScheduleDispatchScheduler.class));
    }
}
