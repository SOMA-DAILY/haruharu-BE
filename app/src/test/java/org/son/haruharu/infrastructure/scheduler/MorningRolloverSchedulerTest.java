package org.son.haruharu.infrastructure.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.business.SlotStartNotifier;
import org.son.haruharu.domain.room.business.ConsolationRewardService;
import org.son.haruharu.domain.room.business.RoomActivator;
import org.son.haruharu.domain.room.business.RoomTerminator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MorningRolloverSchedulerTest {

    @Mock ConsolationRewardService consolationRewardService;
    @Mock RoomTerminator roomTerminator;
    @Mock RoomActivator roomActivator;
    @Mock SlotStartNotifier slotStartNotifier;

    MorningRolloverScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MorningRolloverScheduler(
                consolationRewardService, roomTerminator, roomActivator, slotStartNotifier);
    }

    @Test
    @DisplayName("아침 롤오버는 위로구름 회수 → 세션 종료 → 방 활성화 → 슬롯 시작 알림 순서로 실행된다")
    void whenRollover_thenRunsInDeterministicOrder() {
        scheduler.rollover();

        InOrder inOrder = Mockito.inOrder(
                consolationRewardService, roomTerminator, roomActivator, slotStartNotifier);
        inOrder.verify(consolationRewardService).grantForAnnouncedRooms(any(LocalDateTime.class));
        inOrder.verify(roomTerminator).terminate(any(LocalDateTime.class), anyInt());
        inOrder.verify(roomActivator).activate(any(LocalDate.class), any(LocalDateTime.class));
        inOrder.verify(slotStartNotifier).notifyOpeningSlots(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("앞 단계가 실패해도 뒤 단계(활성화·알림)는 계속 실행된다")
    void givenEarlyStepFails_whenRollover_thenLaterStepsStillRun() {
        given(consolationRewardService.grantForAnnouncedRooms(any())).willThrow(new RuntimeException("boom"));
        given(roomTerminator.terminate(any(), anyInt())).willThrow(new RuntimeException("boom"));

        scheduler.rollover();

        then(roomActivator).should().activate(any(LocalDate.class), any(LocalDateTime.class));
        then(slotStartNotifier).should().notifyOpeningSlots(any(LocalDateTime.class));
    }
}
