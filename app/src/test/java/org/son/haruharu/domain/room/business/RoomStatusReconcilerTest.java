package org.son.haruharu.domain.room.business;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

/**
 * reconciler 는 "대상을 모아 방마다 전이 서비스에 넘기고, 실패를 격리한다"는 오케스트레이션만 책임진다.
 * 전이 규칙 자체(멱등·sticky·앵커·알림)는 {@link RoomStatusTransitionServiceTest} 가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RoomStatusReconcilerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 19, 5);

    @Mock RoomReader roomReader;
    @Mock RoomStatusTransitionService transitionService;

    RoomStatusReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new RoomStatusReconciler(roomReader, transitionService);
    }

    private Room room(Long id) {
        return new Room(id, RoomStatus.PENDING, NOW.toLocalDate(), 3, null, null);
    }

    @Test
    @DisplayName("조회된 방마다 전이를 시도하고, 실제로 전이된 방 수를 반환한다")
    void givenCandidates_whenReconcile_thenCountsTransitioned() {
        given(roomReader.getReconcilable()).willReturn(List.of(room(1L), room(2L), room(3L)));
        given(transitionService.transition(any(), eq(NOW))).willReturn(true, false, true);

        assertThat(reconciler.reconcile(NOW)).isEqualTo(2);
        then(transitionService).should(times(3)).transition(any(), eq(NOW));
    }

    @Test
    @DisplayName("한 방의 전이가 예외로 실패해도 나머지 방은 계속 처리된다")
    void givenOneRoomThrows_whenReconcile_thenOthersStillProcessed() {
        given(roomReader.getReconcilable()).willReturn(List.of(room(1L), room(2L)));
        given(transitionService.transition(any(), eq(NOW)))
                .willThrow(new RuntimeException("flush 실패"))
                .willReturn(true);

        assertThat(reconciler.reconcile(NOW)).isEqualTo(1);
        then(transitionService).should(times(2)).transition(any(), eq(NOW));
    }

    @Test
    @DisplayName("전이 대상이 없으면 0을 반환한다")
    void givenNoCandidates_whenReconcile_thenZero() {
        given(roomReader.getReconcilable()).willReturn(List.of());

        assertThat(reconciler.reconcile(NOW)).isZero();
    }
}
