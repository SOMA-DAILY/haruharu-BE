package org.son.haruharu.infrastructure.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.common.time.AdjustableClock;
import org.son.haruharu.domain.matching.business.MatchingExecutor;
import org.son.haruharu.domain.matching.business.MatchingProperties;
import org.son.haruharu.domain.room.business.ConsolationRewardService;
import org.son.haruharu.domain.room.business.RoomActivator;
import org.son.haruharu.domain.room.business.RoomTerminator;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchingScheduler 자동매칭 활성 플래그")
class MatchingSchedulerTest {

    @Mock
    MatchingExecutor matchingExecutor;
    @Mock
    RoomActivator roomActivator;
    @Mock
    RoomTerminator roomTerminator;
    @Mock
    ConsolationRewardService consolationRewardService;
    @Mock
    ObjectProvider<AdjustableClock> adjustableClockProvider;

    private MatchingScheduler scheduler(boolean matchingEnabled) {
        return new MatchingScheduler(
                matchingExecutor, roomActivator, roomTerminator,
                consolationRewardService, adjustableClockProvider,
                new MatchingProperties(20L, matchingEnabled));
    }

    @Test
    @DisplayName("자동매칭 비활성(enabled=false) 시 매칭 배치는 executor를 호출하지 않는다")
    void givenMatchingDisabled_whenRunMatching_thenExecutorNotCalled() {
        // given
        MatchingScheduler scheduler = scheduler(false);

        // when
        scheduler.runMatching();

        // then
        verify(matchingExecutor, never()).execute(any(), any());
    }

    @Test
    @DisplayName("자동매칭 활성(enabled=true) 시 매칭 배치가 executor를 호출한다")
    void givenMatchingEnabled_whenRunMatching_thenExecutorCalled() {
        // given
        MatchingScheduler scheduler = scheduler(true);

        // when
        scheduler.runMatching();

        // then
        verify(matchingExecutor).execute(any(), any());
    }
}
