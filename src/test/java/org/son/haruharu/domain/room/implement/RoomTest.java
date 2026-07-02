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
}
