package org.son.haruharu.infrastructure.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.common.time.AdjustableClock;
import org.son.haruharu.domain.matching.business.MatchingExecutor;
import org.son.haruharu.domain.matching.business.MatchingProperties;
import org.son.haruharu.domain.notification.business.FinalSelectionOpenNotifier;
import org.son.haruharu.domain.room.business.ConsolationRewardService;
import org.son.haruharu.domain.room.business.RoomActivator;
import org.son.haruharu.domain.room.business.RoomTerminator;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 가상 시계(AdjustableClock) 고정 중에는 방 활성화·종료·위로 구름 배치가 skip 되어야 한다(#180).
 * 고정된 테스트 상태를 실시간 크론이 훼손(방 조기 종료, 위로 구름 오지급)하지 않게 하는 가드.
 */
@ExtendWith(MockitoExtension.class)
class SchedulerVirtualTimeGuardTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock MatchingExecutor matchingExecutor;
    @Mock RoomActivator roomActivator;
    @Mock RoomTerminator roomTerminator;
    @Mock ConsolationRewardService consolationRewardService;
    @Mock FinalSelectionOpenNotifier finalSelectionOpenNotifier;

    @SuppressWarnings("unchecked")
    private ObjectProvider<AdjustableClock> providerOf(AdjustableClock clock) {
        ObjectProvider<AdjustableClock> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(clock);
        return provider;
    }

    private AdjustableClock fixedClock() {
        AdjustableClock clock = new AdjustableClock(Clock.system(SEOUL));
        clock.fix(LocalDateTime.of(2026, 7, 12, 19, 0));
        return clock;
    }

    @Test
    @DisplayName("가상 시계 고정 중이면 방 활성화·종료·위로 구름 배치가 실행되지 않는다")
    void givenFixedClock_whenSchedulersRun_thenRoomBatchesSkipped() {
        MatchingScheduler scheduler = new MatchingScheduler(
                matchingExecutor, roomActivator, roomTerminator, consolationRewardService,
                providerOf(fixedClock()), new MatchingProperties(20L, false));

        scheduler.activateRooms();
        scheduler.terminateSessions();

        verifyNoInteractions(roomActivator, roomTerminator, consolationRewardService);
    }

    @Test
    @DisplayName("가상 시계 고정 중에도 매칭 확정 배치(22:00)는 정상 실행된다 — 큐 기반이라 dev 방과 무관")
    void givenFixedClock_whenRunMatching_thenStillExecutes() {
        // runMatching은 가드하지 않으므로 provider를 조회조차 하지 않는다 — 스텁 없는 mock으로 검증
        @SuppressWarnings("unchecked")
        ObjectProvider<AdjustableClock> untouchedProvider = mock(ObjectProvider.class);
        MatchingScheduler scheduler = new MatchingScheduler(
                matchingExecutor, roomActivator, roomTerminator, consolationRewardService,
                untouchedProvider, new MatchingProperties(20L, true));

        scheduler.runMatching();

        verify(matchingExecutor).execute(any(), any());
        verifyNoInteractions(untouchedProvider);
    }

    @Test
    @DisplayName("가상 시계 빈이 없거나(운영) 고정이 아니면 배치가 정상 실행된다")
    void givenNoOrUnfixedClock_whenSchedulersRun_thenBatchesExecute() {
        MatchingScheduler withoutBean = new MatchingScheduler(
                matchingExecutor, roomActivator, roomTerminator, consolationRewardService,
                providerOf(null), new MatchingProperties(20L, false));
        withoutBean.activateRooms();
        verify(roomActivator).activate(any(), any());

        MatchingScheduler withUnfixed = new MatchingScheduler(
                matchingExecutor, roomActivator, roomTerminator, consolationRewardService,
                providerOf(new AdjustableClock(Clock.system(SEOUL))), new MatchingProperties(20L, false));
        withUnfixed.terminateSessions();
        verify(roomTerminator).terminate(any(), anyInt());
    }

    @Test
    @DisplayName("가상 시계 고정 중이면 위로 구름 발표 배치(22:00)가 실행되지 않는다")
    void givenFixedClock_whenGrantConsolations_thenSkipped() {
        RoomDayScheduler scheduler = new RoomDayScheduler(
                finalSelectionOpenNotifier, consolationRewardService, providerOf(fixedClock()));

        scheduler.grantConsolations();

        verifyNoInteractions(consolationRewardService);
    }
}
