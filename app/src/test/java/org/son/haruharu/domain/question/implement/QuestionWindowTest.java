package org.son.haruharu.domain.question.implement;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class QuestionWindowTest {
    final LocalDateTime startsAt = LocalDateTime.of(2026, 10, 19, 8, 0);
    final LocalDateTime finalSelection = LocalDateTime.of(2026, 10, 21, 18, 0);

    QuestionWindow at(String status, LocalDateTime now) {
        return QuestionWindow.at(status, startsAt, finalSelection, now);
    }

    QuestionWindow at(LocalDateTime now) {
        return at("ACTIVE", now);
    }

    @Test
    @DisplayName("2일차는 시작 다음 날 08:00~24:00, 3일차는 그다음 날 08:00~18:00이다")
    void givenRoom_whenWindow_thenCalendarDayWindows() {
        QuestionWindow window = at(LocalDateTime.of(2026, 10, 19, 12, 0));
        assertThat(window.days())
                .extracting(
                        QuestionWindow.Day::day,
                        QuestionWindow.Day::opensAt,
                        QuestionWindow.Day::closesAt)
                .containsExactly(
                        tuple(
                                2,
                                LocalDateTime.of(2026, 10, 20, 8, 0),
                                LocalDateTime.of(2026, 10, 21, 0, 0)),
                        tuple(
                                3,
                                LocalDateTime.of(2026, 10, 21, 8, 0),
                                LocalDateTime.of(2026, 10, 21, 18, 0)));
        assertThat(window.phase()).isEqualTo("LOCKED");
        assertThat(window.focus().day()).isEqualTo(2);
        assertThat(window.answerOpen()).isFalse();
    }

    @Test
    @DisplayName("08:00 정각에 열리고 자정 정각에 2일차가 닫히며 밤사이에는 답변만 열린다")
    void givenDayTwoBoundaries_whenWindow_thenOpenAtEightAndCloseAtMidnight() {
        assertThat(at(LocalDateTime.of(2026, 10, 20, 7, 59, 59)).openDay()).isEmpty();
        QuestionWindow open = at(LocalDateTime.of(2026, 10, 20, 8, 0));
        assertThat(open.openDay()).map(QuestionWindow.Day::day).contains(2);
        assertThat(open.phase()).isEqualTo("OPEN");
        assertThat(open.answerOpen()).isTrue();
        assertThat(at(LocalDateTime.of(2026, 10, 20, 23, 59, 59)).openDay())
                .map(QuestionWindow.Day::day)
                .contains(2);

        QuestionWindow night = at(LocalDateTime.of(2026, 10, 21, 0, 0));
        assertThat(night.openDay()).isEmpty();
        assertThat(night.days())
                .extracting(QuestionWindow.Day::state)
                .containsExactly("CLOSED", "LOCKED");
        assertThat(night.phase()).isEqualTo("CLOSED");
        assertThat(night.focus().day()).isEqualTo(3);
        assertThat(night.answerOpen()).isTrue();
    }

    @Test
    @DisplayName("3일차는 18:00 정각에 닫히고 최종 선택이 시작되면 답변도 닫힌다")
    void givenDayThreeBoundaries_whenWindow_thenCloseAtSixAndSelection() {
        assertThat(at(LocalDateTime.of(2026, 10, 21, 8, 0)).openDay())
                .map(QuestionWindow.Day::day)
                .contains(3);
        assertThat(at(LocalDateTime.of(2026, 10, 21, 17, 59, 59)).openDay())
                .map(QuestionWindow.Day::day)
                .contains(3);
        QuestionWindow selecting = at("SELECTING", LocalDateTime.of(2026, 10, 21, 18, 0));
        assertThat(selecting.days())
                .extracting(QuestionWindow.Day::state)
                .containsOnly("CLOSED");
        assertThat(selecting.phase()).isEqualTo("CLOSED");
        assertThat(selecting.answerOpen()).isFalse();
        assertThat(selecting.focus().day()).isEqualTo(3);
    }

    @Test
    @DisplayName("최종 선택이 다음 날이면 3일차 질문은 18:00에 닫히고 답변은 선택 전까지 열린다")
    void givenLateSelection_whenEvening_thenSendClosedButAnswerOpen() {
        QuestionWindow window =
                QuestionWindow.at(
                        "ACTIVE",
                        LocalDateTime.of(2026, 10, 17, 7, 0),
                        LocalDateTime.of(2026, 10, 20, 7, 0),
                        LocalDateTime.of(2026, 10, 19, 20, 0));
        assertThat(window.days())
                .extracting(QuestionWindow.Day::state, QuestionWindow.Day::closesAt)
                .containsExactly(
                        tuple("CLOSED", LocalDateTime.of(2026, 10, 19, 0, 0)),
                        tuple("CLOSED", LocalDateTime.of(2026, 10, 19, 18, 0)));
        assertThat(window.answerOpen()).isTrue();
    }

    @Test
    @DisplayName("최종 선택이 더 이르면 그 시각에 닫고, 하루 안에 끝나는 방은 열리지 않는다")
    void givenEarlySelection_whenWindow_thenClampOrNeverOpen() {
        QuestionWindow early =
                QuestionWindow.at(
                        "ACTIVE",
                        startsAt,
                        LocalDateTime.of(2026, 10, 21, 12, 0),
                        LocalDateTime.of(2026, 10, 21, 12, 0));
        assertThat(early.days().get(1).closesAt()).isEqualTo(LocalDateTime.of(2026, 10, 21, 12, 0));
        assertThat(early.openDay()).isEmpty();

        QuestionWindow compressed =
                QuestionWindow.at(
                        "ACTIVE",
                        LocalDateTime.of(2026, 10, 19, 9, 0),
                        LocalDateTime.of(2026, 10, 19, 9, 10),
                        LocalDateTime.of(2026, 10, 19, 9, 5));
        assertThat(compressed.days()).extracting(QuestionWindow.Day::state).containsOnly("CLOSED");
        assertThat(compressed.answerOpen()).isFalse();
    }

    @Test
    @DisplayName("취소·종료 방과 일정이 없는 방은 열리지 않는다")
    void givenClosedOrUnscheduled_whenWindow_thenNotOpen() {
        LocalDateTime dayTwo = LocalDateTime.of(2026, 10, 20, 12, 0);
        assertThat(at("CANCELLED", dayTwo).phase()).isEqualTo("CLOSED");
        assertThat(at("CANCELLED", dayTwo).openDay()).isEmpty();
        assertThat(at("ENDED", dayTwo).answerOpen()).isFalse();
        QuestionWindow unscheduled = QuestionWindow.at("ACTIVE", null, null, dayTwo);
        assertThat(unscheduled.days()).isEmpty();
        assertThat(unscheduled.focus()).isNull();
        assertThat(unscheduled.phase()).isEqualTo("LOCKED");
    }
}
