package org.son.haruharu.domain.room.business;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.room.business.event.MatchConsolationEvent;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ConsolationRewardServiceTest {

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock MatchResultReader matchResultReader;
    @Mock CoinService coinService;
    @Mock ApplicationEventPublisher eventPublisher;

    ConsolationRewardService service;

    private static final long AMOUNT = 5L;
    private final Long roomId = 10L;

    @BeforeEach
    void setUp() {
        service = new ConsolationRewardService(roomReader, roomMemberReader, matchResultReader,
                coinService, new ConsolationRewardProperties(AMOUNT), eventPublisher);
    }

    // startedAt 2026-06-24 09:00 → Day3 = 2026-06-26, 결과 발표 = 2026-06-26 22:00
    private Room activeRoom() {
        return new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), 3,
                LocalDateTime.of(2026, 6, 24, 9, 0), null);
    }

    private RoomMember member(Long id, Long userId) {
        return new RoomMember(id, roomId, userId, "MALE", null, null, LocalDateTime.now());
    }

    @Test
    @DisplayName("Day3 22:00에 미매칭 멤버 전원에게 위로 구름을 지급하고 이벤트를 발행한다")
    void givenAnnouncedRoom_whenGrant_thenUnmatchedGetConsolation() {
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(
                member(20L, 1L), member(21L, 2L), member(22L, 3L)));
        given(matchResultReader.hasMatch(20L)).willReturn(true);  // 매칭 성사자
        given(matchResultReader.hasMatch(21L)).willReturn(false);
        given(matchResultReader.hasMatch(22L)).willReturn(false);
        given(coinService.grantMatchConsolation(anyLong(), anyLong(), anyLong())).willReturn(true);

        int granted = service.grantForAnnouncedRooms(LocalDateTime.of(2026, 6, 26, 22, 0));

        assertThat(granted).isEqualTo(2);
        then(coinService).should().grantMatchConsolation(2L, roomId, AMOUNT);
        then(coinService).should().grantMatchConsolation(3L, roomId, AMOUNT);
        then(coinService).should(never()).grantMatchConsolation(1L, roomId, AMOUNT);

        ArgumentCaptor<MatchConsolationEvent> captor = ArgumentCaptor.forClass(MatchConsolationEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().roomId()).isEqualTo(roomId);
        assertThat(captor.getValue().userIds()).containsExactlyInAnyOrder(2L, 3L);
        assertThat(captor.getValue().amount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("결과 발표(22:00) 전에는 지급하지 않는다")
    void givenBeforeAnnounce_whenGrant_thenNothing() {
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom()));

        int granted = service.grantForAnnouncedRooms(LocalDateTime.of(2026, 6, 26, 21, 59));

        assertThat(granted).isZero();
        then(coinService).should(never()).grantMatchConsolation(anyLong(), anyLong(), anyLong());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("Day3가 아닌 방(Day2)은 지급 대상이 아니다")
    void givenNotFinalDay_whenGrant_thenNothing() {
        Room day2Room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 25), 3,
                LocalDateTime.of(2026, 6, 25, 9, 0), null); // Day2 진행 중(2026-06-26 기준)
        given(roomReader.getLegacyActive()).willReturn(List.of(day2Room));

        int granted = service.grantForAnnouncedRooms(LocalDateTime.of(2026, 6, 26, 22, 0));

        assertThat(granted).isZero();
        then(coinService).should(never()).grantMatchConsolation(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("22:00 실행을 놓쳐도 익일 종료 배치 시각(08:59)에 회수 지급된다")
    void givenMissedRun_whenGrantNextMorning_thenRecovered() {
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(member(20L, 1L)));
        given(matchResultReader.hasMatch(20L)).willReturn(false);
        given(coinService.grantMatchConsolation(anyLong(), anyLong(), anyLong())).willReturn(true);

        // 익일 08:59 — raw day는 아직 4 미만 경계 전이라 currentDay=3 유지, 22:00 경과
        int granted = service.grantForAnnouncedRooms(LocalDateTime.of(2026, 6, 27, 8, 59));

        assertThat(granted).isEqualTo(1);
        then(coinService).should().grantMatchConsolation(1L, roomId, AMOUNT);
    }

    @Test
    @DisplayName("이미 지급된 멤버(원장 존재)는 이벤트 대상에서도 제외된다(멱등)")
    void givenAlreadyGranted_whenGrant_thenSkippedAndNoEvent() {
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(member(20L, 1L)));
        given(matchResultReader.hasMatch(20L)).willReturn(false);
        given(coinService.grantMatchConsolation(1L, roomId, AMOUNT)).willReturn(false); // 기지급

        int granted = service.grantForAnnouncedRooms(LocalDateTime.of(2026, 6, 26, 22, 0));

        assertThat(granted).isZero();
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("시작 전(PENDING) 방은 지급 대상이 아니다")
    void givenPendingRoom_whenGrant_thenNothing() {
        Room pending = new Room(roomId, RoomStatus.PENDING, LocalDate.of(2026, 6, 27), 3, null, null);
        given(roomReader.getLegacyActive()).willReturn(List.of(pending));

        int granted = service.grantForAnnouncedRooms(LocalDateTime.of(2026, 6, 26, 22, 0));

        assertThat(granted).isZero();
        then(coinService).should(never()).grantMatchConsolation(anyLong(), anyLong(), anyLong());
    }
}
