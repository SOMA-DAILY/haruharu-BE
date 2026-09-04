package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RoomTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 6, 24, 9, 0);

    private Room activeRoom(int totalDays, LocalDateTime startedAt) {
        return new Room(null, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), totalDays, startedAt, null);
    }

    // ── #311 초대코드 모델: 스케줄 시각 + 파생 상태 ──
    private static final LocalDateTime SCHED_START = LocalDateTime.of(2026, 6, 24, 14, 0);

    private Room scheduledRoom() {
        return Room.createScheduled(SCHED_START, SCHED_START.plusHours(4), SCHED_START.plusHours(6),
                SCHED_START.plusHours(7), SCHED_START.plusHours(8), 3);
    }

    @Test
    @DisplayName("Room의 모든 불변 전이는 스케줄 시각을 보존한다")
    void givenRoomWithSchedule_whenStatusChanges_thenScheduleIsPreserved() {
        Room room = scheduledRoom();

        assertThat(room.materialized(RoomStatus.ACTIVE).startsAt()).isEqualTo(SCHED_START);
        assertThat(room.cancelled().endsAt()).isEqualTo(SCHED_START.plusHours(8));
        assertThat(room.started(SCHED_START).startsAt()).isEqualTo(SCHED_START);
        assertThat(room.ended(SCHED_START.plusDays(3)).endsAt()).isEqualTo(SCHED_START.plusHours(8));
    }

    @Test
    @DisplayName("createScheduled: PENDING·시각 세팅·scheduledDate=시작일")
    void givenTimes_whenCreateScheduled_thenPendingWithTimes() {
        Room room = scheduledRoom();
        assertThat(room.status()).isEqualTo(RoomStatus.PENDING);
        assertThat(room.startsAt()).isEqualTo(SCHED_START);
        assertThat(room.finalSelectionOpensAt()).isEqualTo(SCHED_START.plusHours(4));
        assertThat(room.scheduledDate()).isEqualTo(SCHED_START.toLocalDate());
    }

    @Test
    @DisplayName("statusAt: 시작 전이면 PENDING")
    void givenBeforeStart_whenStatusAt_thenPending() {
        assertThat(scheduledRoom().statusAt(SCHED_START.minusMinutes(1))).isEqualTo(RoomStatus.PENDING);
    }

    @Test
    @DisplayName("statusAt: 시작~선택오픈 전이면 ACTIVE")
    void givenBetweenStartAndOpens_whenStatusAt_thenActive() {
        assertThat(scheduledRoom().statusAt(SCHED_START.plusHours(1))).isEqualTo(RoomStatus.ACTIVE);
    }

    @Test
    @DisplayName("statusAt: 선택오픈~종료 전이면 SELECTING")
    void givenAfterOpensBeforeEnds_whenStatusAt_thenSelecting() {
        assertThat(scheduledRoom().statusAt(SCHED_START.plusHours(5))).isEqualTo(RoomStatus.SELECTING);
    }

    @Test
    @DisplayName("statusAt: 종료 시각 이후면 ENDED")
    void givenAfterEnds_whenStatusAt_thenEnded() {
        assertThat(scheduledRoom().statusAt(SCHED_START.plusHours(9))).isEqualTo(RoomStatus.ENDED);
    }

    @Test
    @DisplayName("statusAt: CANCELLED는 시각과 무관하게 sticky")
    void givenCancelled_whenStatusAt_thenCancelled() {
        assertThat(scheduledRoom().cancelled().statusAt(SCHED_START.plusHours(5))).isEqualTo(RoomStatus.CANCELLED);
    }

    @Test
    @DisplayName("statusAt: 스케줄 시각 없는 구 모델 방은 저장 status를 반환한다")
    void givenLegacyRoomNoTimes_whenStatusAt_thenStoredStatus() {
        Room legacy = new Room(1L, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), 3, STARTED_AT, null);
        assertThat(legacy.statusAt(LocalDateTime.of(2026, 6, 24, 12, 0))).isEqualTo(RoomStatus.ACTIVE);
    }

    // ── #320 withStatus: reconciler 가 쓰는 status 전이 ──

    @Test
    @DisplayName("materialized는 status를 바꾸고 스케줄 시각은 보존한다")
    void givenRoom_whenMaterialized_thenStatusChangedTimesKept() {
        Room room = new Room(7L, RoomStatus.PENDING, SCHED_START.toLocalDate(), 3, null, null,
                SCHED_START, SCHED_START.plusHours(4), SCHED_START.plusHours(6),
                SCHED_START.plusHours(7), SCHED_START.plusHours(8));

        Room active = room.materialized(RoomStatus.ACTIVE);

        assertThat(active.status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(active.id()).isEqualTo(7L);
        assertThat(active.startsAt()).isEqualTo(SCHED_START);
        assertThat(active.finalSelectionOpensAt()).isEqualTo(SCHED_START.plusHours(4));
        assertThat(active.endsAt()).isEqualTo(SCHED_START.plusHours(8));
    }

    @Test
    @DisplayName("materialized로 SELECTING 전이가 가능하다 (started/ended로는 갈 수 없던 상태)")
    void givenActiveRoom_whenMaterializedSelecting_thenSelecting() {
        Room room = scheduledRoom().materialized(RoomStatus.ACTIVE);
        assertThat(room.materialized(RoomStatus.SELECTING).status()).isEqualTo(RoomStatus.SELECTING);
    }

    @Test
    @DisplayName("materialized: PENDING에서 SELECTING·ENDED로 직행해도 startedAt이 앵커된다 (배치 장기 중단 복구)")
    void givenPendingSkippingActive_whenMaterialized_thenStartedAtStillAnchored() {
        Room pending = threeDayRoom().materialized(RoomStatus.PENDING);
        assertThat(pending.startedAt()).isNull();

        assertThat(pending.materialized(RoomStatus.SELECTING).startedAt()).isEqualTo(SCHED_START);
        assertThat(pending.materialized(RoomStatus.ENDED).startedAt()).isEqualTo(SCHED_START);
    }

    @Test
    @DisplayName("materialized: 이미 앵커된 startedAt은 덮어쓰지 않는다")
    void givenAlreadyAnchored_whenMaterialized_thenKeepsOriginalAnchor() {
        LocalDateTime manualAnchor = SCHED_START.plusMinutes(7);
        Room anchored = new Room(1L, RoomStatus.ACTIVE, SCHED_START.toLocalDate(), 3, manualAnchor, null,
                SCHED_START, SCHED_START.plusDays(2), SCHED_START.plusDays(2).plusHours(3),
                SCHED_START.plusDays(2).plusHours(4), SCHED_START.plusDays(3));

        assertThat(anchored.materialized(RoomStatus.SELECTING).startedAt()).isEqualTo(manualAnchor);
    }

    @Test
    @DisplayName("materialized: 구 모델 방은 startedAt을 건드리지 않는다")
    void givenLegacyRoom_whenMaterialized_thenStartedAtUntouched() {
        Room legacy = new Room(1L, RoomStatus.PENDING, LocalDate.of(2026, 6, 24), 3, null, null);
        assertThat(legacy.materialized(RoomStatus.ACTIVE).startedAt()).isNull();
    }

    // ── #320 currentDayAt: 새 모델은 starts_at 상대 24시간 ──

    @Test
    @DisplayName("currentDayAt: 새 모델은 starts_at 기준 24시간 단위로 Day가 넘어간다 (9시 경계 아님)")
    void givenScheduledRoom_whenCurrentDayAt_thenDerivedFromStartsAt() {
        // starts_at = 2026-06-24 14:00, totalDays = 3
        Room room = threeDayRoom();

        assertThat(room.currentDayAt(SCHED_START)).isEqualTo(1);
        assertThat(room.currentDayAt(SCHED_START.plusHours(23))).isEqualTo(1);
        assertThat(room.currentDayAt(SCHED_START.plusDays(1))).isEqualTo(2);
        assertThat(room.currentDayAt(SCHED_START.plusDays(2))).isEqualTo(3);
    }

    @Test
    @DisplayName("currentDayAt: 새 모델은 totalDays로 캡된다")
    void givenScheduledRoom_whenPastLastDay_thenCappedAtTotalDays() {
        assertThat(threeDayRoom().currentDayAt(SCHED_START.plusDays(2).plusHours(12))).isEqualTo(3);
    }

    @Test
    @DisplayName("currentDayAt: 시작 전(PENDING)이면 0")
    void givenBeforeStart_whenCurrentDayAt_thenZero() {
        assertThat(threeDayRoom().currentDayAt(SCHED_START.minusMinutes(1))).isZero();
    }

    @Test
    @DisplayName("currentDayAt: 종료·취소된 방이면 0")
    void givenEndedOrCancelled_whenCurrentDayAt_thenZero() {
        Room room = threeDayRoom();
        // endsAt = starts + 3일
        assertThat(room.currentDayAt(SCHED_START.plusDays(3).plusMinutes(1))).isZero();
        assertThat(room.cancelled().currentDayAt(SCHED_START.plusDays(1))).isZero();
    }

    @Test
    @DisplayName("currentDayAt: 저녁 시작 방이 14시간 만에 Day2가 되지 않는다 (9시 경계 회귀)")
    void givenEveningStart_whenNextMorning_thenStillDay1() {
        LocalDateTime eveningStart = LocalDateTime.of(2026, 8, 20, 19, 0);
        Room room = new Room(1L, RoomStatus.ACTIVE, eveningStart.toLocalDate(), 3, null, null,
                eveningStart, eveningStart.plusDays(2).withHour(18), eveningStart.plusDays(2).withHour(21),
                eveningStart.plusDays(2).withHour(22), eveningStart.plusDays(3).withHour(9));

        // 다음날 09:00 — 구 로직(9시 경계)이라면 Day2, 새 로직(24h)이라면 아직 Day1
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 8, 21, 9, 0))).isEqualTo(1);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 8, 21, 19, 0))).isEqualTo(2);
    }

    /** starts_at=2026-06-24 14:00 · opens +2일18시 · closes +2일21시 · result +2일22시 · ends +3일 */
    private Room threeDayRoom() {
        return new Room(1L, RoomStatus.ACTIVE, SCHED_START.toLocalDate(), 3, null, null,
                SCHED_START, SCHED_START.plusDays(2).withHour(18), SCHED_START.plusDays(2).withHour(21),
                SCHED_START.plusDays(2).withHour(22), SCHED_START.plusDays(3));
    }

    @Test
    @DisplayName("PENDING 방은 currentDay가 0이다")
    void givenPending_whenCurrentDayAt_thenZero() {
        Room room = new Room(null, RoomStatus.PENDING, LocalDate.of(2026, 6, 24), 3, null, null);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 6, 24, 12, 0))).isZero();
    }

    @Test
    @DisplayName("ACTIVE지만 startedAt이 없으면 currentDay가 0이다")
    void givenActiveWithoutStartedAt_whenCurrentDayAt_thenZero() {
        Room room = activeRoom(3, null);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 6, 24, 12, 0))).isZero();
    }

    @Test
    @DisplayName("시작 당일은 Day1이다")
    void givenStartDay_whenCurrentDayAt_thenDay1() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 6, 24, 23, 0))).isEqualTo(1);
    }

    @Test
    @DisplayName("하루 경계(09:00) 직전(다음날 08:59)까지는 Day1을 유지한다")
    void givenJustBeforeBoundary_whenCurrentDayAt_thenStillDay1() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 6, 25, 8, 59))).isEqualTo(1);
    }

    @Test
    @DisplayName("다음날 09:00 정각에 Day2로 전환된다")
    void givenBoundaryExact_whenCurrentDayAt_thenDay2() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 6, 25, 9, 0))).isEqualTo(2);
    }

    @Test
    @DisplayName("2일 경과 후 09:00엔 Day3이다")
    void givenTwoDaysLater_whenCurrentDayAt_thenDay3() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 6, 26, 9, 0))).isEqualTo(3);
    }

    @Test
    @DisplayName("totalDays를 초과한 경과일은 totalDays로 캡된다")
    void givenBeyondTotalDays_whenCurrentDayAt_thenCappedAtTotalDays() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 7, 1, 12, 0))).isEqualTo(3);
    }

    @Test
    @DisplayName("now가 startedAt보다 이전(clock skew)이어도 Day1로 클램프된다")
    void givenNowBeforeStartedAt_whenCurrentDayAt_thenClampedToDay1() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 6, 23, 12, 0))).isEqualTo(1);
    }

    @Test
    @DisplayName("마지막 날(Day3) 중에는 아직 종료 대상이 아니다")
    void givenLastDay_whenHasEndedBy_thenFalse() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.hasEndedBy(LocalDateTime.of(2026, 6, 26, 12, 0))).isFalse();
    }

    @Test
    @DisplayName("마지막 날 경계(다음날 08:59)까지는 종료 대상이 아니다")
    void givenJustBeforeEndBoundary_whenHasEndedBy_thenFalse() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.hasEndedBy(LocalDateTime.of(2026, 6, 27, 8, 59))).isFalse();
    }

    @Test
    @DisplayName("마지막 날 다음날 09:00(Day+1 첫 경계)부터 종료 대상이 된다")
    void givenEndBoundaryExact_whenHasEndedBy_thenTrue() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.hasEndedBy(LocalDateTime.of(2026, 6, 27, 9, 0))).isTrue();
    }

    @Test
    @DisplayName("PENDING 방은 종료 대상이 아니다")
    void givenPending_whenHasEndedBy_thenFalse() {
        Room room = new Room(null, RoomStatus.PENDING, LocalDate.of(2026, 6, 24), 3, null, null);
        assertThat(room.hasEndedBy(LocalDateTime.of(2026, 7, 1, 9, 0))).isFalse();
    }

    @Test
    @DisplayName("이미 ENDED인 방은 종료 대상이 아니다")
    void givenEnded_whenHasEndedBy_thenFalse() {
        Room room = new Room(1L, RoomStatus.ENDED, LocalDate.of(2026, 6, 24), 3, STARTED_AT,
                LocalDateTime.of(2026, 6, 27, 9, 0));
        assertThat(room.hasEndedBy(LocalDateTime.of(2026, 7, 1, 9, 0))).isFalse();
    }

    @Test
    @DisplayName("ended()는 ACTIVE 방을 ENDED로 전환하고 endedAt을 앵커한다")
    void givenActiveRoom_whenEnded_thenStatusEndedWithAnchor() {
        Room room = activeRoom(3, STARTED_AT);
        LocalDateTime endedAt = LocalDateTime.of(2026, 6, 27, 9, 0);

        Room result = room.ended(endedAt);

        assertThat(result.status()).isEqualTo(RoomStatus.ENDED);
        assertThat(result.endedAt()).isEqualTo(endedAt);
        assertThat(result.startedAt()).isEqualTo(STARTED_AT);
        assertThat(result.totalDays()).isEqualTo(3);
    }

    // ── #320 리뷰 반영: 새 모델 방의 startedAt·dateOfDay·announcedAt ──

    @Test
    @DisplayName("materialized(ACTIVE)는 startedAt을 starts_at으로 앵커한다")
    void givenScheduledPending_whenMaterializedActive_thenStartedAtAnchored() {
        Room room = threeDayRoom().materialized(RoomStatus.PENDING);

        Room active = room.materialized(RoomStatus.ACTIVE);

        assertThat(active.status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(active.startedAt()).isEqualTo(SCHED_START);
        assertThat(active.startsAt()).isEqualTo(SCHED_START);
    }

    @Test
    @DisplayName("dateOfDay: 새 모델은 starts_at 날짜 기준으로 Day N의 날짜를 준다 (9시 경계 아님)")
    void givenScheduledRoom_whenDateOfDay_thenDerivedFromStartsAt() {
        Room room = threeDayRoom();
        assertThat(room.dateOfDay(1)).isEqualTo(SCHED_START.toLocalDate());
        assertThat(room.dateOfDay(3)).isEqualTo(SCHED_START.toLocalDate().plusDays(2));
    }

    @Test
    @DisplayName("dateOfDay: 새 모델 방은 startedAt이 null이어도 NPE가 나지 않는다 (오늘 화면 500 회귀)")
    void givenScheduledRoomWithoutStartedAt_whenDateOfDay_thenNoNpe() {
        Room room = threeDayRoom();
        assertThat(room.startedAt()).isNull();
        assertThatCode(() -> room.dateOfDay(1)).doesNotThrowAnyException();
        assertThatCode(() -> room.announcedAt()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("announcedAt: 새 모델은 어드민이 입력한 result_at 을 그대로 발표 시각으로 쓴다")
    void givenScheduledRoom_whenAnnouncedAt_thenResultAt() {
        Room room = threeDayRoom();
        assertThat(room.announcedAt()).isEqualTo(SCHED_START.plusDays(2).withHour(22));
    }

    @Test
    @DisplayName("dateOfDay·announcedAt: 구 모델은 기존 9시 경계 계산을 그대로 유지한다 (회귀 방지)")
    void givenLegacyRoom_whenDateOfDayAndAnnouncedAt_thenLegacyCalculation() {
        Room legacy = activeRoom(3, STARTED_AT); // startedAt = 2026-06-24 09:00
        assertThat(legacy.dateOfDay(1)).isEqualTo(LocalDate.of(2026, 6, 24));
        assertThat(legacy.dateOfDay(3)).isEqualTo(LocalDate.of(2026, 6, 26));
        assertThat(legacy.announcedAt())
                .isEqualTo(LocalDateTime.of(2026, 6, 26, 22, 0));
    }
}
