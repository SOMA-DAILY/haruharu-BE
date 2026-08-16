package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.command.CreateRoomCommand;
import org.son.haruharu.domain.room.business.dto.RoomResponse;
import org.son.haruharu.domain.room.business.exception.RoomAlreadyEndedException;
import org.son.haruharu.domain.room.business.exception.RoomInvalidScheduleException;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.business.exception.RoomStartsAtInPastException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock RoomReader roomReader;
    @Mock RoomWriter roomWriter;
    @Mock RoomMemberReader roomMemberReader;

    RoomService roomService;

    // 2026-06-26 12:00 KST 고정 → startedAt 앵커에 따라 currentDay가 파생된다.
    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 6, 26, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomReader, roomWriter, roomMemberReader, clock);
    }

    @Test
    @DisplayName("유효한 스케줄로 방 생성 시 PENDING 방을 저장하고 roomId를 반환한다")
    void givenValidSchedule_whenCreateRoom_thenSavesAndReturnsId() {
        LocalDateTime starts = LocalDateTime.of(2026, 6, 26, 14, 0);
        CreateRoomCommand cmd = new CreateRoomCommand(
                starts, starts.plusDays(2).withHour(20), starts.plusDays(2).withHour(22),
                starts.plusDays(2).withHour(23), starts.plusDays(2).withHour(23).withMinute(30), 3);
        given(roomWriter.save(any())).willReturn(new Room(99L, RoomStatus.PENDING, starts.toLocalDate(), 3,
                null, null, false, null, cmd.startsAt(), cmd.finalSelectionOpensAt(),
                cmd.finalSelectionClosesAt(), cmd.resultAt(), cmd.endsAt()));

        Long id = roomService.createRoom(cmd);

        assertThat(id).isEqualTo(99L);
    }

    @Test
    @DisplayName("시각 순서가 어긋난 스케줄로 방 생성 시 RoomInvalidScheduleException")
    void givenMisorderedSchedule_whenCreateRoom_thenThrows() {
        LocalDateTime t = LocalDateTime.of(2026, 6, 26, 14, 0);
        // 선택오픈이 시작보다 앞 → 순서 위반
        CreateRoomCommand bad = new CreateRoomCommand(
                t, t.minusHours(1), t.plusHours(2), t.plusHours(3), t.plusHours(4), 3);

        assertThatThrownBy(() -> roomService.createRoom(bad))
                .isInstanceOf(RoomInvalidScheduleException.class);
    }

    // ── #320 ⑥ starts_at 과거 시각 거부 (고정 시계 = 2026-06-26 12:00) ──

    @Test
    @DisplayName("starts_at이 현재보다 이전이면 RoomStartsAtInPastException — 방이 저장되지 않는다")
    void givenPastStartsAt_whenCreateRoom_thenThrows() {
        LocalDateTime past = LocalDateTime.of(2026, 6, 26, 11, 59); // 고정 시계보다 1분 전
        CreateRoomCommand cmd = new CreateRoomCommand(
                past, past.plusHours(4), past.plusHours(6), past.plusHours(7), past.plusHours(8), 3);

        assertThatThrownBy(() -> roomService.createRoom(cmd))
                .isInstanceOf(RoomStartsAtInPastException.class);
        verify(roomWriter, never()).save(any());
    }

    @Test
    @DisplayName("starts_at이 현재와 같으면 허용된다 (거부 조건은 '현재보다 이전', 유예 없음)")
    void givenStartsAtEqualsNow_whenCreateRoom_thenCreated() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 26, 12, 0); // 고정 시계와 동일
        CreateRoomCommand cmd = new CreateRoomCommand(
                now, now.plusHours(4), now.plusHours(6), now.plusHours(7), now.plusHours(8), 3);
        given(roomWriter.save(any())).willReturn(new Room(101L, RoomStatus.PENDING, now.toLocalDate(), 3,
                null, null, false, null, cmd.startsAt(), cmd.finalSelectionOpensAt(),
                cmd.finalSelectionClosesAt(), cmd.resultAt(), cmd.endsAt()));

        assertThat(roomService.createRoom(cmd)).isEqualTo(101L);
    }

    // ── #320 ④ 방 취소 ──

    @Test
    @DisplayName("방 취소 시 CANCELLED로 저장되고 이후 statusAt도 CANCELLED다")
    void givenPendingRoom_whenCancel_thenCancelled() {
        LocalDateTime starts = LocalDateTime.of(2026, 6, 27, 19, 0);
        Room room = new Room(77L, RoomStatus.PENDING, starts.toLocalDate(), 3, null, null, false, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(3),
                starts.plusDays(2).plusHours(4), starts.plusDays(3));
        given(roomReader.findById(77L)).willReturn(Optional.of(room));
        given(roomWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        roomService.cancel(77L);

        verify(roomWriter).save(argThat(r -> r.status() == RoomStatus.CANCELLED
                && r.statusAt(starts.plusDays(1)) == RoomStatus.CANCELLED));
    }

    @Test
    @DisplayName("이미 종료된 방은 취소할 수 없다")
    void givenEndedRoom_whenCancel_thenThrows() {
        LocalDateTime starts = LocalDateTime.of(2026, 6, 20, 19, 0);
        Room ended = new Room(78L, RoomStatus.ENDED, starts.toLocalDate(), 3, null, null, false, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(3),
                starts.plusDays(2).plusHours(4), starts.plusDays(3));
        given(roomReader.findById(78L)).willReturn(Optional.of(ended));

        assertThatThrownBy(() -> roomService.cancel(78L))
                .isInstanceOf(RoomAlreadyEndedException.class);
        verify(roomWriter, never()).save(any());
    }

    @Test
    @DisplayName("없는 방을 취소하면 RoomNotFoundException")
    void givenUnknownRoom_whenCancel_thenNotFound() {
        given(roomReader.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.cancel(999L))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    @DisplayName("종료되지 않은 방에 속한 유저가 조회 시 방 정보와 멤버 수를 반환한다")
    void givenActiveRoom_whenGetMyRoom_thenReturnsRoom() {
        Long userId = 1L;
        Long roomId = 10L;
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.now(), 3, LocalDateTime.now(), null);

        given(roomMemberReader.findByUserId(userId))
            .willReturn(List.of(new RoomMember(51L, roomId, userId, "MALE", null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(roomId), LocalDateTime.of(2026, 6, 26, 12, 0))).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(
            new RoomMember(52L, roomId, userId, "MALE", null, LocalDateTime.now()),
            new RoomMember(53L, roomId, 2L, "FEMALE", null, LocalDateTime.now())
        ));

        RoomResponse result = roomService.getMyRoom(userId);

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.memberCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("속한 방이 없으면 RoomNotFoundException이 발생한다")
    void givenNoRoom_whenGetMyRoom_thenThrows() {
        Long userId = 1L;
        given(roomMemberReader.findByUserId(userId)).willReturn(List.of());
        given(roomReader.findCurrent(List.of(), LocalDateTime.of(2026, 6, 26, 12, 0))).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getMyRoom(userId))
            .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    @DisplayName("방 멤버가 roomId로 조회 시 방 정보를 반환한다")
    void givenMember_whenGetRoom_thenReturnsRoom() {
        Long userId = 1L;
        Long roomId = 20L;
        // startedAt=전날 09:00 → clock(2026-06-26 12:00) 기준 currentDay=2로 파생
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 25), 3,
            LocalDateTime.of(2026, 6, 25, 9, 0), null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willReturn(new RoomMember(54L, roomId, userId, "MALE", null, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(
            new RoomMember(55L, roomId, userId, "MALE", null, LocalDateTime.now())
        ));

        RoomResponse result = roomService.getRoom(roomId, userId);

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.currentDay()).isEqualTo(2);
    }

    @Test
    @DisplayName("방 멤버가 아닌 유저가 roomId로 조회 시 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetRoom_thenThrows() {
        Long userId = 1L;
        Long roomId = 30L;
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> roomService.getRoom(roomId, userId))
            .isInstanceOf(NotRoomMemberException.class);
    }

    @Test
    @DisplayName("종료되지 않은 방이 있는 유저는 hasCurrentRoom이 true다")
    void givenCurrentRoom_whenHasCurrentRoom_thenTrue() {
        Long userId = 1L;
        Long roomId = 40L;
        given(roomMemberReader.findByUserId(userId))
            .willReturn(List.of(new RoomMember(56L, roomId, userId, "MALE", null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(roomId), LocalDateTime.of(2026, 6, 26, 12, 0))).willReturn(Optional.of(
            new Room(roomId, RoomStatus.ACTIVE, LocalDate.now(), 3, LocalDateTime.now(), null)));

        assertThat(roomService.hasCurrentRoom(userId)).isTrue();
    }

    @Test
    @DisplayName("과거 방만 있거나 방 이력이 없는 유저는 hasCurrentRoom이 false다")
    void givenNoCurrentRoom_whenHasCurrentRoom_thenFalse() {
        Long withEndedOnly = 1L;
        Long endedRoomId = 50L;
        given(roomMemberReader.findByUserId(withEndedOnly))
            .willReturn(List.of(new RoomMember(57L, endedRoomId, withEndedOnly, "MALE", null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(endedRoomId), LocalDateTime.of(2026, 6, 26, 12, 0))).willReturn(Optional.empty());

        Long noHistory = 2L;
        given(roomMemberReader.findByUserId(noHistory)).willReturn(List.of());

        assertThat(roomService.hasCurrentRoom(withEndedOnly)).isFalse();
        assertThat(roomService.hasCurrentRoom(noHistory)).isFalse();
    }

    // ── #320 Codex 리뷰 반영: 배치가 밀린 구간에서 종료된 방이 "내 현재 방"으로 새지 않는다 ──

    /** ends_at 은 지났지만 저장 status 는 아직 ACTIVE 인 방 (5분 배치 전 / 배치 장애 구간) */
    private Room staleEndedRoom(Long roomId) {
        LocalDateTime starts = LocalDateTime.of(2026, 6, 20, 19, 0);
        return new Room(roomId, RoomStatus.ACTIVE, starts.toLocalDate(), 3, starts, null, false, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(3),
                starts.plusDays(2).plusHours(4), starts.plusDays(3)); // endsAt = 6/23 19:00 < now(6/26 12:00)
    }

    @Test
    @DisplayName("ends_at이 지난 방은 저장 status가 ACTIVE여도 내 현재 방으로 반환되지 않는다")
    void givenStaleEndedRoom_whenFindMyRoom_thenEmpty() {
        Long userId = 1L;
        given(roomMemberReader.findByUserId(userId))
                .willReturn(List.of(new RoomMember(1L, 10L, userId, "MALE", null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(10L), LocalDateTime.of(2026, 6, 26, 12, 0)))
                .willReturn(Optional.empty());

        assertThat(roomService.findMyRoom(userId)).isEmpty();
    }

    @Test
    @DisplayName("hasCurrentRoom은 시각 기준으로 판단한다 — 끝난 방이 다음 방 입장을 막지 않는다")
    void givenStaleEndedRoom_whenHasCurrentRoom_thenFalse() {
        Long userId = 1L;
        given(roomMemberReader.findByUserId(userId))
                .willReturn(List.of(new RoomMember(1L, 10L, userId, "MALE", null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(10L), LocalDateTime.of(2026, 6, 26, 12, 0)))
                .willReturn(Optional.empty());

        assertThat(roomService.hasCurrentRoom(userId)).isFalse();
    }
}
