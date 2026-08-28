package org.son.haruharu.devsupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.common.time.AdjustableClock;
import org.son.haruharu.devsupport.dto.DevRoomResponse;
import org.son.haruharu.devsupport.exception.DevMemberAlreadyInRoomException;
import org.son.haruharu.devsupport.exception.DevMemberNotVerifiedException;
import org.son.haruharu.devsupport.exception.DevRoomNotActiveException;
import org.son.haruharu.devsupport.exception.InvalidDevDayException;
import org.son.haruharu.devsupport.exception.InvalidDevMembersException;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.matching.business.MatchingExecutor;
import org.son.haruharu.domain.mission.business.RoomMissionSlotService;
import org.son.haruharu.domain.room.business.ConsolationRewardService;
import org.son.haruharu.domain.room.business.RoomActivator;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DevRoomServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 실시간: 2026-07-10 14:00 KST */
    private static final LocalDateTime REAL_NOW = LocalDateTime.of(2026, 7, 10, 14, 0);

    @Mock IdentityService identityService;
    @Mock RoomService roomService;
    @Mock RoomReader roomReader;
    @Mock RoomWriter roomWriter;
    @Mock RoomMemberWriter roomMemberWriter;
    @Mock MatchingExecutor matchingExecutor;
    @Mock RoomActivator roomActivator;
    @Mock ConsolationRewardService consolationRewardService;
    @Mock RoomMissionSlotService roomMissionSlotService;
    @Mock DevRoomCleaner devRoomCleaner;

    AdjustableClock adjustableClock;
    DevRoomService devRoomService;

    @BeforeEach
    void setUp() {
        adjustableClock = new AdjustableClock(Clock.fixed(REAL_NOW.atZone(SEOUL).toInstant(), SEOUL));
        devRoomService = new DevRoomService(identityService, roomService, roomReader, roomWriter,
                roomMemberWriter, matchingExecutor, roomActivator, consolationRewardService,
                roomMissionSlotService, devRoomCleaner, adjustableClock);
    }

    private void givenVerifiedMembers(Long... userIds) {
        for (Long id : userIds) {
            given(identityService.findVerified(id))
                    .willReturn(Optional.of(new VerifiedIdentity(id % 2 == 0 ? "MALE" : "FEMALE",
                            LocalDate.of(1998, 3, 14))));
            given(roomService.hasCurrentRoom(id)).willReturn(false);
        }
    }

    private void givenRoomSaveEchoesWithId(long roomId) {
        given(roomWriter.save(any(Room.class))).willAnswer(inv -> {
            Room r = inv.getArgument(0);
            return new Room(roomId, r.status(), r.scheduledDate(), r.totalDays(), r.startedAt(), r.endedAt());
        });
    }

    // ── createRoom ──────────────────────────────────────────────

    @Test
    @DisplayName("day 지정 없이 생성하면 오늘 09:00 시작(started_at)인 ACTIVE 방이 만들어진다")
    void givenNoDay_whenCreateRoom_thenActiveRoomAnchoredAtToday9am() {
        givenVerifiedMembers(112L, 115L);
        givenRoomSaveEchoesWithId(2L);

        DevRoomResponse response = devRoomService.createRoom(List.of(112L, 115L), null, null);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomWriter).save(captor.capture());
        Room saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(saved.startedAt()).isEqualTo(LocalDateTime.of(2026, 7, 10, 9, 0));
        assertThat(saved.scheduledDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(response.currentDay()).isEqualTo(1);
        assertThat(adjustableClock.isFixed()).isFalse();
        verify(roomMissionSlotService).createDefaultsForRoom(
                2L, 3, LocalDateTime.of(2026, 7, 10, 9, 0), LocalDateTime.of(2026, 7, 13, 9, 0));
    }

    @Test
    @DisplayName("day=3, time=19:00으로 생성하면 started_at은 이틀 전 09:00, 가상 시계는 Day3 19:00에 고정된다")
    void givenDay3Time19_whenCreateRoom_thenBackdatedAndClockFixed() {
        givenVerifiedMembers(112L, 115L);
        givenRoomSaveEchoesWithId(2L);

        devRoomService.createRoom(List.of(112L, 115L), 3, LocalTime.of(19, 0));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomWriter).save(captor.capture());
        assertThat(captor.getValue().startedAt()).isEqualTo(LocalDateTime.of(2026, 7, 8, 9, 0));
        assertThat(adjustableClock.fixedAt()).contains(LocalDateTime.of(2026, 7, 10, 19, 0));
        // 고정 시각 기준 currentDay가 요청한 day와 일치
        assertThat(captor.getValue().currentDayAt(LocalDateTime.now(adjustableClock))).isEqualTo(3);
    }

    @Test
    @DisplayName("time이 09시 이전(새벽)이면 해당 Day의 익일 달력 날짜로 고정된다")
    void givenTimeBefore9am_whenCreateRoom_thenFixedAtNextCalendarDate() {
        givenVerifiedMembers(112L, 115L);
        givenRoomSaveEchoesWithId(2L);

        devRoomService.createRoom(List.of(112L, 115L), 2, LocalTime.of(2, 0));

        // day2 앵커 날짜는 07-10(내일 아님 주의: startDate 07-09 + 1) → 새벽 02:00은 07-11
        assertThat(adjustableClock.fixedAt()).contains(LocalDateTime.of(2026, 7, 11, 2, 0));
    }

    @Test
    @DisplayName("멤버가 저장될 때 identity의 성별·생년월일이 기록된다")
    void givenVerifiedMembers_whenCreateRoom_thenMembersCarryIdentityData() {
        givenVerifiedMembers(112L, 115L);
        givenRoomSaveEchoesWithId(2L);

        devRoomService.createRoom(List.of(112L, 115L), null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RoomMember>> captor = ArgumentCaptor.forClass(List.class);
        verify(roomMemberWriter).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2)
                .allSatisfy(m -> {
                    assertThat(m.roomId()).isEqualTo(2L);
                    assertThat(m.gender()).isIn("MALE", "FEMALE");
                    assertThat(m.birthday()).isNotNull();
                });
    }

    @Test
    @DisplayName("멤버가 2명 미만이면 예외가 발생한다")
    void givenSingleMember_whenCreateRoom_thenThrows() {
        assertThatThrownBy(() -> devRoomService.createRoom(List.of(112L), null, null))
                .isInstanceOf(InvalidDevMembersException.class);
    }

    @Test
    @DisplayName("멤버가 6명을 초과하면 예외가 발생한다")
    void givenSevenMembers_whenCreateRoom_thenThrows() {
        assertThatThrownBy(() -> devRoomService.createRoom(
                List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), null, null))
                .isInstanceOf(InvalidDevMembersException.class);
    }

    @Test
    @DisplayName("멤버 id가 중복되면 예외가 발생한다")
    void givenDuplicateMembers_whenCreateRoom_thenThrows() {
        assertThatThrownBy(() -> devRoomService.createRoom(List.of(112L, 112L), null, null))
                .isInstanceOf(InvalidDevMembersException.class);
    }

    @Test
    @DisplayName("본인인증이 없는 멤버가 포함되면 예외가 발생한다")
    void givenUnverifiedMember_whenCreateRoom_thenThrows() {
        given(identityService.findVerified(112L))
                .willReturn(Optional.of(new VerifiedIdentity("MALE", LocalDate.of(1991, 2, 11))));
        given(roomService.hasCurrentRoom(112L)).willReturn(false);
        given(identityService.findVerified(110L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> devRoomService.createRoom(List.of(112L, 110L), null, null))
                .isInstanceOf(DevMemberNotVerifiedException.class);
    }

    @Test
    @DisplayName("이미 진행 중인 방이 있는 멤버가 포함되면 예외가 발생한다")
    void givenMemberInActiveRoom_whenCreateRoom_thenThrows() {
        given(identityService.findVerified(112L))
                .willReturn(Optional.of(new VerifiedIdentity("MALE", LocalDate.of(1991, 2, 11))));
        given(roomService.hasCurrentRoom(112L)).willReturn(true);

        assertThatThrownBy(() -> devRoomService.createRoom(List.of(112L, 115L), null, null))
                .isInstanceOf(DevMemberAlreadyInRoomException.class);
    }

    @Test
    @DisplayName("day가 1~totalDays 범위를 벗어나면 예외가 발생한다")
    void givenDayOutOfRange_whenCreateRoom_thenThrows() {
        assertThatThrownBy(() -> devRoomService.createRoom(List.of(112L, 115L), 4, null))
                .isInstanceOf(InvalidDevDayException.class);
        assertThatThrownBy(() -> devRoomService.createRoom(List.of(112L, 115L), 0, null))
                .isInstanceOf(InvalidDevDayException.class);
    }

    // ── setRoomTime ─────────────────────────────────────────────

    private Room activeRoomStarted0708() {
        return new Room(2L, RoomStatus.ACTIVE, LocalDate.of(2026, 7, 8), 3,
                LocalDateTime.of(2026, 7, 8, 9, 0), null);
    }

    @Test
    @DisplayName("Day3 19:00으로 지정하면 가상 시계가 그 방의 Day3 19:00 절대 시각에 고정된다")
    void givenActiveRoom_whenSetTimeDay3_thenClockFixed() {
        given(roomReader.findById(2L)).willReturn(Optional.of(activeRoomStarted0708()));

        devRoomService.setRoomTime(2L, 3, LocalTime.of(19, 0));

        assertThat(adjustableClock.fixedAt()).contains(LocalDateTime.of(2026, 7, 10, 19, 0));
    }

    @Test
    @DisplayName("새벽 시각(09시 이전)은 해당 Day의 익일 달력 날짜로 고정된다")
    void givenActiveRoom_whenSetTimeBefore9am_thenFixedAtNextCalendarDate() {
        given(roomReader.findById(2L)).willReturn(Optional.of(activeRoomStarted0708()));

        devRoomService.setRoomTime(2L, 3, LocalTime.of(2, 0));

        assertThat(adjustableClock.fixedAt()).contains(LocalDateTime.of(2026, 7, 11, 2, 0));
    }

    @Test
    @DisplayName("day가 totalDays를 넘으면 예외가 발생한다 (Day3 슬롯 재개방 엣지 차단)")
    void givenDay4_whenSetTime_thenThrows() {
        given(roomReader.findById(2L)).willReturn(Optional.of(activeRoomStarted0708()));

        assertThatThrownBy(() -> devRoomService.setRoomTime(2L, 4, LocalTime.of(10, 0)))
                .isInstanceOf(InvalidDevDayException.class);
        assertThat(adjustableClock.isFixed()).isFalse();
    }

    @Test
    @DisplayName("시작 전(PENDING) 방에 시간을 지정하면 예외가 발생한다")
    void givenPendingRoom_whenSetTime_thenThrows() {
        given(roomReader.findById(1L)).willReturn(Optional.of(
                new Room(1L, RoomStatus.PENDING, LocalDate.of(2026, 7, 10), 3, null, null)));

        assertThatThrownBy(() -> devRoomService.setRoomTime(1L, 1, LocalTime.of(10, 0)))
                .isInstanceOf(DevRoomNotActiveException.class);
    }

    // ── terminate / consolation / matching ─────────────────────

    @Test
    @DisplayName("ACTIVE 방을 수동 종료하면 ENDED로 저장된다")
    void givenActiveRoom_whenTerminate_thenSavedAsEnded() {
        given(roomReader.findById(2L)).willReturn(Optional.of(activeRoomStarted0708()));

        devRoomService.terminateRoom(2L);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomWriter).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(RoomStatus.ENDED);
        assertThat(captor.getValue().endedAt()).isNotNull();
    }

    @Test
    @DisplayName("ACTIVE가 아닌 방을 종료하려 하면 예외가 발생한다")
    void givenPendingRoom_whenTerminate_thenThrows() {
        given(roomReader.findById(1L)).willReturn(Optional.of(
                new Room(1L, RoomStatus.PENDING, LocalDate.of(2026, 7, 10), 3, null, null)));

        assertThatThrownBy(() -> devRoomService.terminateRoom(1L))
                .isInstanceOf(DevRoomNotActiveException.class);
    }

    @Test
    @DisplayName("위로 구름 수동 트리거는 가상 시각 기준으로 기존 유스케이스를 호출한다")
    void givenFixedClock_whenGrantConsolation_thenDelegatesWithVirtualNow() {
        LocalDateTime virtual = LocalDateTime.of(2026, 7, 10, 22, 30);
        adjustableClock.fix(virtual);
        given(consolationRewardService.grantForAnnouncedRooms(virtual)).willReturn(4);

        int granted = devRoomService.grantConsolation();

        assertThat(granted).isEqualTo(4);
    }

    @Test
    @DisplayName("매칭 수동 실행은 확정과 활성화를 순서대로 호출한다")
    void givenQueuedUsers_whenRunMatching_thenExecutesAndActivates() {
        given(matchingExecutor.execute(any(LocalDate.class), any(LocalDateTime.class))).willReturn(1);
        given(roomActivator.activate(any(LocalDate.class), any(LocalDateTime.class))).willReturn(1);

        var result = devRoomService.runMatching(null);

        assertThat(result.roomsCreated()).isEqualTo(1);
        assertThat(result.roomsActivated()).isEqualTo(1);
        verify(matchingExecutor).execute(LocalDate.of(2026, 7, 10), REAL_NOW);
    }
}
