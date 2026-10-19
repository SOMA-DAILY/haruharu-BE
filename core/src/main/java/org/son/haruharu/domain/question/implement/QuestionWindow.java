package org.son.haruharu.domain.question.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 익명 질문 일정. 일차는 방 시작 날짜 기준 달력 날짜다(2일차 = 시작 날짜 + 1일).
 *
 * <ul>
 *   <li>질문 보내기: 2일차 08:00~24:00, 3일차 08:00~min(18:00, 최종 선택 시작). 상대마다 날마다 1번.
 *   <li>답변: 2일차 08:00부터 최종 선택 시작 전까지 밤에도 가능하다.
 * </ul>
 *
 * <p>{@code phase}·{@code opensAt}·{@code closesAt}은 옛 앱이 읽는 값이라 "지금(또는 다음) 날" 기준으로 둔다.
 */
public record QuestionWindow(
        String phase,
        LocalDateTime opensAt,
        LocalDateTime closesAt,
        List<Day> days,
        boolean answerOpen) {
    static final LocalTime OPENS = LocalTime.of(8, 0);
    static final LocalTime LAST_DAY_CLOSES = LocalTime.of(18, 0);

    public record Day(int day, String state, LocalDateTime opensAt, LocalDateTime closesAt) {
        public boolean open() {
            return "OPEN".equals(state);
        }
    }

    public static QuestionWindow at(
            String roomStatus,
            LocalDateTime startsAt,
            LocalDateTime finalSelectionOpensAt,
            LocalDateTime now) {
        boolean finished =
                "CANCELLED".equals(roomStatus)
                        || "ENDED".equals(roomStatus)
                        || "SELECTING".equals(roomStatus);
        if (startsAt == null || finalSelectionOpensAt == null)
            return new QuestionWindow(finished ? "CLOSED" : "LOCKED", null, null, List.of(), false);
        boolean active = "ACTIVE".equals(roomStatus);
        LocalDate first = startsAt.toLocalDate();
        List<Day> days = new ArrayList<>();
        for (int day = 2; day <= 3; day++) {
            LocalDate date = first.plusDays(day - 1L);
            LocalDateTime opens = date.atTime(OPENS);
            LocalDateTime end = day == 2 ? date.plusDays(1).atStartOfDay() : date.atTime(LAST_DAY_CLOSES);
            LocalDateTime closes = end.isBefore(finalSelectionOpensAt) ? end : finalSelectionOpensAt;
            String state;
            if (finished || !opens.isBefore(closes)) state = "CLOSED";
            else if (now.isBefore(opens)) state = "LOCKED";
            else state = active && now.isBefore(closes) ? "OPEN" : "CLOSED";
            days.add(new Day(day, state, opens, closes));
        }
        LocalDateTime answerFrom = days.getFirst().opensAt();
        boolean answerOpen =
                active && !now.isBefore(answerFrom) && now.isBefore(finalSelectionOpensAt);
        Day focus = focus(days);
        String phase =
                days.stream().anyMatch(Day::open)
                        ? "OPEN"
                        : !finished && now.isBefore(answerFrom) ? "LOCKED" : "CLOSED";
        return new QuestionWindow(
                phase, focus.opensAt(), focus.closesAt(), List.copyOf(days), answerOpen);
    }

    /** 처음 보여줄 날: 열린 날 → 다음에 열릴 날 → 마지막 날. 일정이 없으면 null. */
    public Day focus() {
        return days.isEmpty() ? null : focus(days);
    }

    public Optional<Day> openDay() {
        return days.stream().filter(Day::open).findFirst();
    }

    private static Day focus(List<Day> days) {
        return days.stream()
                .filter(Day::open)
                .findFirst()
                .or(() -> days.stream().filter(d -> "LOCKED".equals(d.state())).findFirst())
                .orElse(days.getLast());
    }
}
