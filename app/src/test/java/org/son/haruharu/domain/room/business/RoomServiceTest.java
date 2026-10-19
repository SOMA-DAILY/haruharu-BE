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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortReader;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.son.haruharu.domain.cohort.storage.exception.CohortNotFoundException;
import org.son.haruharu.domain.room.implement.RoomComposition;
import org.son.haruharu.domain.room.implement.exception.InvalidRoomCompositionException;

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
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @Mock RoomMemberReader roomMemberReader;
    @Mock CohortReader cohortReader;

    RoomService roomService;

    // 2026-06-26 12:00 KST 고정 → startedAt 앵커에 따라 currentDay가 파생된다.
    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 6, 26, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomReader, roomWriter, roomMemberReader, cohortReader, clock, events);
    }

    @Test
    @DisplayName("유효한 스케줄로 방 생성 시 PENDING 방을 저장하고 roomId를 반환한다")
    void givenValidSchedule_whenCreateRoom_thenSavesAndReturnsId() {
        LocalDateTime starts = LocalDateTime.of(2026, 6, 26, 14, 0);
        CreateRoomCommand cmd = new CreateRoomCommand(
                starts, starts.plusDays(2).withHour(20), starts.plusDays(2).withHour(22),
                starts.plusDays(2).withHour(23), starts.plusDays(2).withHour(23).withMinute(30), 3);
        given(roomWriter.save(any())).willReturn(new Room(99L, RoomStatus.PENDING, starts.toLocalDate(), 3,
                null, null, cmd.startsAt(), cmd.finalSelectionOpensAt(),
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

    // ── #513 기수·이름·성별 정원 ──

    private CreateRoomCommand composedCommand(Long cohortId, String name, Integer male, Integer female) {
        LocalDateTime starts = LocalDateTime.of(2026, 6, 26, 14, 0);
        return new CreateRoomCommand(starts, starts.plusDays(2).withHour(20), starts.plusDays(2).withHour(22),
                starts.plusDays(2).withHour(23), starts.plusDays(2).withHour(23).withMinute(30), 3,
                cohortId, name, male, female);
    }

    private Cohort cohort(Long id, CohortStatus status) {
        return new Cohort(id, 1L, 6, status, LocalDateTime.of(2026, 6, 20, 0, 0), LocalDateTime.of(2026, 6, 25, 0, 0),
                LocalDateTime.of(2026, 6, 26, 0, 0), null, null, null, null, "admin:1", null, null);
    }

    @ParameterizedTest
    @EnumSource(value = CohortStatus.class, names = {"DRAFT", "OPEN"})
    @DisplayName("편성 중(DRAFT·OPEN)인 기수·이름·성별 정원을 주면 기수를 공유 잠금으로 확인하고 방에 함께 저장한다")
    void givenComposition_whenCreateRoom_thenValidatesCohortAndSavesComposition(CohortStatus status) {
        given(cohortReader.getByIdForShare(4L)).willReturn(cohort(4L, status));
        given(roomWriter.save(any())).willAnswer(inv -> {
            Room room = inv.getArgument(0);
            return new Room(99L, room.status(), room.scheduledDate(), room.totalDays(), null, null, room.startsAt(),
                    room.finalSelectionOpensAt(), room.finalSelectionClosesAt(), room.resultAt(), room.endsAt(),
                    room.composition());
        });

        assertThat(roomService.createRoom(composedCommand(4L, " 서울 6기 1조 ", 3, 3))).isEqualTo(99L);

        verify(roomWriter).save(argThat(room -> room.composition().equals(RoomComposition.of(4L, "서울 6기 1조", 3, 3))));
    }

    @Test
    @DisplayName("기수 없이 이름·정원만 주는 방(웹 전용 방)은 기수를 조회하지 않는다")
    void givenNoCohort_whenCreateRoom_thenSkipsCohortLookup() {
        given(roomWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        roomService.createRoom(composedCommand(null, "웹 3:3", 3, 3));

        verify(cohortReader, never()).getByIdForShare(any());
        verify(roomWriter).save(argThat(room -> room.composition().cohortId() == null
                && "웹 3:3".equals(room.composition().name())));
    }

    @ParameterizedTest
    @EnumSource(value = CohortStatus.class, names = {"SETTLED", "CANCELLED"})
    @DisplayName("정산·취소된 기수에는 방을 만들지 않는다")
    void givenClosedCohort_whenCreateRoom_thenRejects(CohortStatus status) {
        given(cohortReader.getByIdForShare(4L)).willReturn(cohort(4L, status));

        assertThatThrownBy(() -> roomService.createRoom(composedCommand(4L, null, 3, 3)))
                .isInstanceOfSatisfying(InvalidRoomCompositionException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(error -> error.field()).containsExactly("cohort_id"));
        verify(roomWriter, never()).save(any());
    }

    @Test
    @DisplayName("없는 기수면 COHORT_NOT_FOUND 로 거부하고 방을 저장하지 않는다")
    void givenMissingCohort_whenCreateRoom_thenNotFound() {
        given(cohortReader.getByIdForShare(404L)).willThrow(new CohortNotFoundException());

        assertThatThrownBy(() -> roomService.createRoom(composedCommand(404L, null, null, null)))
                .isInstanceOf(CohortNotFoundException.class);
        verify(roomWriter, never()).save(any());
    }

    @Test
    @DisplayName("성별 정원이 1 미만이면 기수를 조회하기 전에 거부한다")
    void givenNonPositiveCapacity_whenCreateRoom_thenRejectsBeforeCohortLookup() {
        assertThatThrownBy(() -> roomService.createRoom(composedCommand(4L, null, 0, 3)))
                .isInstanceOf(InvalidRoomCompositionException.class);
        verify(cohortReader, never()).getByIdForShare(any());
        verify(roomWriter, never()).save(any());
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
                null, null, cmd.startsAt(), cmd.finalSelectionOpensAt(),
                cmd.finalSelectionClosesAt(), cmd.resultAt(), cmd.endsAt()));

        assertThat(roomService.createRoom(cmd)).isEqualTo(101L);
    }

    // ── #320 ④ 방 취소 ──

    @Test
    @DisplayName("방 취소 시 CANCELLED로 저장되고 이후 statusAt도 CANCELLED다")
    void givenPendingRoom_whenCancel_thenCancelled() {
        LocalDateTime starts = LocalDateTime.of(2026, 6, 27, 19, 0);
        Room room = new Room(77L, RoomStatus.PENDING, starts.toLocalDate(), 3, null, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(3),
                starts.plusDays(2).plusHours(4), starts.plusDays(3));
        given(roomReader.findByIdForUpdate(77L)).willReturn(Optional.of(room));
        given(roomWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        roomService.cancel(77L);

        verify(roomWriter).save(argThat(r -> r.status() == RoomStatus.CANCELLED
                && r.statusAt(starts.plusDays(1)) == RoomStatus.CANCELLED));
        verify(roomReader, never()).findById(77L);
    }

    @Test
    @DisplayName("이미 종료된 방은 취소할 수 없다")
    void givenEndedRoom_whenCancel_thenThrows() {
        LocalDateTime starts = LocalDateTime.of(2026, 6, 20, 19, 0);
        Room ended = new Room(78L, RoomStatus.ENDED, starts.toLocalDate(), 3, null, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(3),
                starts.plusDays(2).plusHours(4), starts.plusDays(3));
        given(roomReader.findByIdForUpdate(78L)).willReturn(Optional.of(ended));

        assertThatThrownBy(() -> roomService.cancel(78L))
                .isInstanceOf(RoomAlreadyEndedException.class);
        verify(roomWriter, never()).save(any());
    }

    @Test
    @DisplayName("없는 방을 취소하면 RoomNotFoundException")
    void givenUnknownRoom_whenCancel_thenNotFound() {
        given(roomReader.findByIdForUpdate(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.cancel(999L))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    @DisplayName("종료되지 않은 방에 속한 유저가 조회 시 방 정보와 멤버 수를 반환한다(구 모델 방은 starts_at·ends_at을 scheduledDate로 보정)")
    void givenActiveRoom_whenGetMyRoom_thenReturnsRoom() {
        Long userId = 1L;
        Long roomId = 10L;
        // 구 모델 방(6-arg): starts_at·ends_at 미설정 → scheduledDate 기반 09:00 보정으로 non-null 노출
        LocalDate scheduled = LocalDate.of(2026, 6, 25);
        Room room = new Room(roomId, RoomStatus.ACTIVE, scheduled, 3, LocalDateTime.now(), null);

        given(roomMemberReader.findByUserId(userId))
            .willReturn(List.of(new RoomMember(51L, roomId, userId, "MALE", null, null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(roomId), LocalDateTime.of(2026, 6, 26, 12, 0))).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(
            new RoomMember(52L, roomId, userId, "MALE", null, null, LocalDateTime.now()),
            new RoomMember(53L, roomId, 2L, "FEMALE", null, null, LocalDateTime.now())
        ));

        RoomResponse result = roomService.getMyRoom(userId);

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.memberCount()).isEqualTo(2);
        // 구 모델 fallback: scheduledDate 09:00 시작, +3일 09:00 종료(홈 카운트다운 회귀 방지)
        assertThat(result.startsAt()).isEqualTo(scheduled.atTime(9, 0));
        assertThat(result.endsAt()).isEqualTo(scheduled.plusDays(3).atTime(9, 0));
    }

    @Test
    @DisplayName("새 모델 방은 어드민이 입력한 starts_at·ends_at을 그대로 노출한다")
    void givenScheduledRoom_whenGetMyRoom_thenExposesAdminTimes() {
        Long userId = 1L;
        Long roomId = 12L;
        LocalDateTime startsAt = LocalDateTime.of(2026, 6, 26, 19, 0);
        LocalDateTime endsAt = LocalDateTime.of(2026, 6, 29, 9, 0);
        Room room = new Room(roomId, RoomStatus.ACTIVE, startsAt.toLocalDate(), 3, startsAt, null,
            startsAt, startsAt.plusDays(2), startsAt.plusDays(2).plusHours(2),
            startsAt.plusDays(2).plusHours(3), endsAt);

        given(roomMemberReader.findByUserId(userId))
            .willReturn(List.of(new RoomMember(60L, roomId, userId, "MALE", null, null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(roomId), LocalDateTime.of(2026, 6, 26, 12, 0))).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(
            new RoomMember(60L, roomId, userId, "MALE", null, null, LocalDateTime.now())));

        RoomResponse result = roomService.getMyRoom(userId);

        assertThat(result.startsAt()).isEqualTo(startsAt);
        assertThat(result.endsAt()).isEqualTo(endsAt);
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
            .willReturn(new RoomMember(54L, roomId, userId, "MALE", null, null, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(
            new RoomMember(55L, roomId, userId, "MALE", null, null, LocalDateTime.now())
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
            .willReturn(List.of(new RoomMember(56L, roomId, userId, "MALE", null, null, LocalDateTime.now())));
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
            .willReturn(List.of(new RoomMember(57L, endedRoomId, withEndedOnly, "MALE", null, null, LocalDateTime.now())));
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
        return new Room(roomId, RoomStatus.ACTIVE, starts.toLocalDate(), 3, starts, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(3),
                starts.plusDays(2).plusHours(4), starts.plusDays(3)); // endsAt = 6/23 19:00 < now(6/26 12:00)
    }

    @Test
    @DisplayName("ends_at이 지난 방은 저장 status가 ACTIVE여도 내 현재 방으로 반환되지 않는다")
    void givenStaleEndedRoom_whenFindMyRoom_thenEmpty() {
        Long userId = 1L;
        given(roomMemberReader.findByUserId(userId))
                .willReturn(List.of(new RoomMember(1L, 10L, userId, "MALE", null, null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(10L), LocalDateTime.of(2026, 6, 26, 12, 0)))
                .willReturn(Optional.empty());

        assertThat(roomService.findMyRoom(userId)).isEmpty();
    }

    @Test
    @DisplayName("hasCurrentRoom은 시각 기준으로 판단한다 — 끝난 방이 다음 방 입장을 막지 않는다")
    void givenStaleEndedRoom_whenHasCurrentRoom_thenFalse() {
        Long userId = 1L;
        given(roomMemberReader.findByUserId(userId))
                .willReturn(List.of(new RoomMember(1L, 10L, userId, "MALE", null, null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(10L), LocalDateTime.of(2026, 6, 26, 12, 0)))
                .willReturn(Optional.empty());

        assertThat(roomService.hasCurrentRoom(userId)).isFalse();
    }
}
