package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomTest {

    private static final int DAY_START_HOUR = 9; // UploadSlot.dayStartHour()
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 6, 24, 9, 0);

    private Room activeRoom(int totalDays, LocalDateTime startedAt) {
        return new Room(null, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), totalDays, startedAt, null);
    }

    @Test
    @DisplayName("PENDING 방은 currentDay가 0이다")
    void givenPending_whenCurrentDayAt_thenZero() {
        Room room = new Room(null, RoomStatus.PENDING, LocalDate.of(2026, 6, 24), 3, null, null);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 6, 24, 12, 0), DAY_START_HOUR)).isZero();
    }

    @Test
    @DisplayName("ACTIVE지만 startedAt이 없으면 currentDay가 0이다")
    void givenActiveWithoutStartedAt_whenCurrentDayAt_thenZero() {
        Room room = activeRoom(3, null);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 6, 24, 12, 0), DAY_START_HOUR)).isZero();
    }

    @Test
    @DisplayName("시작 당일은 Day1이다")
    void givenStartDay_whenCurrentDayAt_thenDay1() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 6, 24, 23, 0), DAY_START_HOUR)).isEqualTo(1);
    }

    @Test
    @DisplayName("하루 경계(09:00) 직전(다음날 08:59)까지는 Day1을 유지한다")
    void givenJustBeforeBoundary_whenCurrentDayAt_thenStillDay1() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 6, 25, 8, 59), DAY_START_HOUR)).isEqualTo(1);
    }

    @Test
    @DisplayName("다음날 09:00 정각에 Day2로 전환된다")
    void givenBoundaryExact_whenCurrentDayAt_thenDay2() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 6, 25, 9, 0), DAY_START_HOUR)).isEqualTo(2);
    }

    @Test
    @DisplayName("2일 경과 후 09:00엔 Day3이다")
    void givenTwoDaysLater_whenCurrentDayAt_thenDay3() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 6, 26, 9, 0), DAY_START_HOUR)).isEqualTo(3);
    }

    @Test
    @DisplayName("totalDays를 초과한 경과일은 totalDays로 캡된다")
    void givenBeyondTotalDays_whenCurrentDayAt_thenCappedAtTotalDays() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 7, 1, 12, 0), DAY_START_HOUR)).isEqualTo(3);
    }

    @Test
    @DisplayName("now가 startedAt보다 이전(clock skew)이어도 Day1로 클램프된다")
    void givenNowBeforeStartedAt_whenCurrentDayAt_thenClampedToDay1() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.currentDayAt(LocalDateTime.of(2026, 6, 23, 12, 0), DAY_START_HOUR)).isEqualTo(1);
    }

    @Test
    @DisplayName("마지막 날(Day3) 중에는 아직 종료 대상이 아니다")
    void givenLastDay_whenHasEndedBy_thenFalse() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.hasEndedBy(LocalDateTime.of(2026, 6, 26, 12, 0), DAY_START_HOUR)).isFalse();
    }

    @Test
    @DisplayName("마지막 날 경계(다음날 08:59)까지는 종료 대상이 아니다")
    void givenJustBeforeEndBoundary_whenHasEndedBy_thenFalse() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.hasEndedBy(LocalDateTime.of(2026, 6, 27, 8, 59), DAY_START_HOUR)).isFalse();
    }

    @Test
    @DisplayName("마지막 날 다음날 09:00(Day+1 첫 경계)부터 종료 대상이 된다")
    void givenEndBoundaryExact_whenHasEndedBy_thenTrue() {
        Room room = activeRoom(3, STARTED_AT);
        assertThat(room.hasEndedBy(LocalDateTime.of(2026, 6, 27, 9, 0), DAY_START_HOUR)).isTrue();
    }

    @Test
    @DisplayName("PENDING 방은 종료 대상이 아니다")
    void givenPending_whenHasEndedBy_thenFalse() {
        Room room = new Room(null, RoomStatus.PENDING, LocalDate.of(2026, 6, 24), 3, null, null);
        assertThat(room.hasEndedBy(LocalDateTime.of(2026, 7, 1, 9, 0), DAY_START_HOUR)).isFalse();
    }

    @Test
    @DisplayName("이미 ENDED인 방은 종료 대상이 아니다")
    void givenEnded_whenHasEndedBy_thenFalse() {
        Room room = new Room(1L, RoomStatus.ENDED, LocalDate.of(2026, 6, 24), 3, STARTED_AT,
                LocalDateTime.of(2026, 6, 27, 9, 0));
        assertThat(room.hasEndedBy(LocalDateTime.of(2026, 7, 1, 9, 0), DAY_START_HOUR)).isFalse();
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

    // ===================== 데모 방(#265) =====================

    @Test
    @DisplayName("데모 방은 실시간이 흘러도 currentDay를 저장된 demoDay로 고정한다")
    void givenDemoRoom_whenCurrentDayAt_thenReturnsDemoDayRegardlessOfClock() {
        Room demo = Room.createDemo(LocalDate.of(2026, 6, 24), 3, 2, STARTED_AT);
        // 시작 한참 전(Day 파생이면 0)·한참 후(Day 파생이면 3)여도 demoDay=2 고정
        assertThat(demo.currentDayAt(LocalDateTime.of(2026, 6, 24, 9, 0), DAY_START_HOUR)).isEqualTo(2);
        assertThat(demo.currentDayAt(LocalDateTime.of(2026, 7, 1, 9, 0), DAY_START_HOUR)).isEqualTo(2);
    }

    @Test
    @DisplayName("데모 방 Day 진행은 demoDay를 갱신하고 currentDay에 반영된다")
    void givenDemoRoom_whenWithDemoDay_thenCurrentDayReflects() {
        Room demo = Room.createDemo(LocalDate.of(2026, 6, 24), 3, 1, STARTED_AT);
        Room advanced = demo.withDemoDay(3);
        assertThat(advanced.currentDayAt(LocalDateTime.of(2026, 6, 24, 9, 0), DAY_START_HOUR)).isEqualTo(3);
    }

    @Test
    @DisplayName("데모 방은 시각이 아무리 지나도 hasEndedBy가 false다(무만료)")
    void givenDemoRoom_whenHasEndedBy_thenAlwaysFalse() {
        Room demo = Room.createDemo(LocalDate.of(2026, 6, 24), 3, 3, STARTED_AT);
        assertThat(demo.hasEndedBy(LocalDateTime.of(2026, 12, 31, 9, 0), DAY_START_HOUR)).isFalse();
    }
}
