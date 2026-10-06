package org.son.haruharu.domain.room.business;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.event.FinalSelectionOpenedEvent;
import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RoomStatusTransitionServiceTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 8, 20, 19, 0);
    private static final LocalDateTime OPENS_AT = LocalDateTime.of(2026, 8, 22, 18, 0);
    private static final LocalDateTime CLOSES_AT = LocalDateTime.of(2026, 8, 22, 21, 0);
    private static final LocalDateTime RESULT_AT = LocalDateTime.of(2026, 8, 22, 22, 0);
    private static final LocalDateTime ENDS_AT = LocalDateTime.of(2026, 8, 23, 9, 0);

    @Mock RoomReader roomReader;
    @Mock RoomWriter roomWriter;
    @Mock ApplicationEventPublisher eventPublisher;

    RoomStatusTransitionService service;

    @BeforeEach
    void setUp() {
        service = new RoomStatusTransitionService(roomReader, roomWriter, eventPublisher);
    }

    private Room roomWith(RoomStatus stored) {
        return new Room(1L, stored, STARTS_AT.toLocalDate(), 3, null, null,
                STARTS_AT, OPENS_AT, CLOSES_AT, RESULT_AT, ENDS_AT);
    }

    private boolean transition(Room room, LocalDateTime now) {
        given(roomReader.findByIdForUpdate(room.id())).willReturn(Optional.of(room));
        return service.transition(room, now);
    }

    @Test
    @DisplayName("PENDING → ACTIVE 전이 시 startedAt을 starts_at으로 앵커하고 ROOM_STARTED를 발행한다")
    void givenPastStartsAt_whenTransition_thenActiveWithStartedAtAnchor() {
        boolean changed = transition(roomWith(RoomStatus.PENDING), STARTS_AT.plusMinutes(5));

        assertThat(changed).isTrue();
        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        then(roomWriter).should().save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(RoomStatus.ACTIVE);
        // startedAt 을 비워두면 dateOfDay·announcedAt 이 NPE 로 터진다 — 앵커 필수.
        assertThat(captor.getValue().startedAt()).isEqualTo(STARTS_AT);
        then(eventPublisher).should().publishEvent(new RoomStartedEvent(1L));
    }

    @Test
    @DisplayName("ACTIVE → SELECTING 전이 시 FinalSelectionOpenedEvent를 발행한다")
    void givenPastOpensAt_whenTransition_thenSelectingAndEvent() {
        boolean changed = transition(roomWith(RoomStatus.ACTIVE), OPENS_AT.plusMinutes(1));

        assertThat(changed).isTrue();
        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        then(roomWriter).should().save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(RoomStatus.SELECTING);
        then(eventPublisher).should().publishEvent(new FinalSelectionOpenedEvent(1L));
    }

    @Test
    @DisplayName("PENDING → SELECTING 직행(배치 장기 중단)에도 startedAt 앵커·ROOM_STARTED·최종선택 오픈 이벤트가 모두 발행된다")
    void givenPendingSkippingActive_whenTransition_thenStartedAtAnchoredAndEvents() {
        transition(roomWith(RoomStatus.PENDING), OPENS_AT.plusMinutes(1));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        then(roomWriter).should().save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(RoomStatus.SELECTING);
        // startedAt 이 비면 MatchResultService 가 "미시작"으로 보고 결과 발표를 숨긴다.
        assertThat(captor.getValue().startedAt()).isEqualTo(STARTS_AT);
        // ACTIVE 를 건너뛰어도 "방 시작"은 한 번 발행돼야 GROUP 채팅이 생성된다.
        then(eventPublisher).should().publishEvent(new RoomStartedEvent(1L));
        then(eventPublisher).should().publishEvent(new FinalSelectionOpenedEvent(1L));
    }

    @Test
    @DisplayName("PENDING → ENDED 직행에도 startedAt 앵커·ROOM_STARTED가 발행된다(채팅 누락 방지)")
    void givenPendingSkippingToEnded_whenTransition_thenStartedAtAnchoredAndRoomStarted() {
        transition(roomWith(RoomStatus.PENDING), ENDS_AT.plusHours(1));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        then(roomWriter).should().save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(RoomStatus.ENDED);
        assertThat(captor.getValue().startedAt()).isEqualTo(STARTS_AT);
        then(eventPublisher).should().publishEvent(new RoomStartedEvent(1L));
    }

    @Test
    @DisplayName("ENDED 전이에는 알림이 없다")
    void givenPastEndsAt_whenTransition_thenEndedWithoutEvent() {
        boolean changed = transition(roomWith(RoomStatus.SELECTING), ENDS_AT.plusMinutes(1));

        assertThat(changed).isTrue();
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("파생 상태가 저장 상태와 같으면 저장·알림 모두 하지 않는다 (멱등)")
    void givenAlreadyInSync_whenTransition_thenNoop() {
        boolean changed = transition(roomWith(RoomStatus.ACTIVE), STARTS_AT.plusHours(1));

        assertThat(changed).isFalse();
        then(roomWriter).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("CANCELLED 방은 시각이 지나도 전이하지 않는다 (sticky)")
    void givenCancelled_whenTransition_thenNoop() {
        boolean changed = transition(roomWith(RoomStatus.PENDING).cancelled(), STARTS_AT.plusHours(2));

        assertThat(changed).isFalse();
        then(roomWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("스케줄 시각이 없는 구 모델 방은 전이하지 않는다")
    void givenLegacyRoom_whenTransition_thenNoop() {
        Room legacy = new Room(2L, RoomStatus.PENDING, STARTS_AT.toLocalDate(), 3, null, null);

        boolean changed = transition(legacy, STARTS_AT.plusHours(2));

        assertThat(changed).isFalse();
        then(roomWriter).should(never()).save(any());
    }
}
