package org.son.haruharu.domain.question.business.dto;

import org.son.haruharu.domain.question.implement.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 익명 질문 목록. {@code days}는 날짜(2·3일차)별 상대 상태다. {@code phase}·{@code targets}·
 * {@code remainingCount} 등 기존 칸은 옛 앱이 읽으므로 "지금(또는 다음) 날" 기준으로 채우고 옛 상태 값만 쓴다.
 */
public record QuestionListResponse(
        String phase,
        LocalDateTime opensAt,
        LocalDateTime closesAt,
        int remainingCount,
        boolean grantUnread,
        boolean hasUnread,
        long receivedCount,
        LatestEvent latestEvent,
        List<Target> targets,
        List<Received> received,
        Long receivedNextCursor,
        Integer currentDay,
        List<Day> days) {
    public record Target(
            Long roomMemberId,
            String nickname,
            String avatarUrl,
            Long questionId,
            String status,
            boolean unread) {}

    public record Day(
            int day,
            String state,
            LocalDateTime opensAt,
            LocalDateTime closesAt,
            int remainingCount,
            List<Target> targets) {}

    public record Received(
            Long id, String status, boolean unread, LocalDateTime createdAt, int day) {}

    public record LatestEvent(String key, String type, Long questionId) {}

    public static QuestionListResponse from(
            QuestionContext c, QuestionReader.Page page, boolean grantUnread) {
        List<Question> questions = page.questions();
        List<Question> sent =
                questions.stream().filter(q -> q.senderUserId().equals(c.userId())).toList();
        QuestionWindow window = c.window();
        QuestionWindow.Day focus = window.focus();
        List<Day> days = new ArrayList<>();
        for (QuestionWindow.Day day : window.days()) {
            List<Target> targets = targets(c, sent, day, true);
            days.add(
                    new Day(
                            day.day(),
                            day.state(),
                            day.opensAt(),
                            day.closesAt(),
                            available(targets),
                            targets));
        }
        // 옛 앱용: 열린 날이면 그날 상대(다시 보낼 수 있게), 아니면 날짜를 합쳐 보낸 기록이 모두 보이게 한다.
        // 옛 앱이 모르는 LOCKED 는 CLOSED 로 보낸다.
        boolean focusOpen = focus != null && focus.open();
        List<Target> legacy =
                targets(c, sent, focus, focusOpen).stream()
                                .map(
                                        t ->
                                                t.status().equals("LOCKED")
                                                        ? new Target(
                                                                t.roomMemberId(),
                                                                t.nickname(),
                                                                t.avatarUrl(),
                                                                t.questionId(),
                                                                "CLOSED",
                                                                t.unread())
                                                        : t)
                                .toList();
        List<Question> incoming =
                questions.stream()
                        .filter(q -> q.recipientUserId().equals(c.userId()) && c.visible(q))
                        .sorted(Comparator.comparing(Question::id).reversed())
                        .toList();
        List<Received> received =
                incoming.stream()
                        .map(
                                q ->
                                        new Received(
                                                q.id(),
                                                status(c, q),
                                                q.unreadFor(c.userId()),
                                                q.createdAt(),
                                                q.day()))
                        .toList();
        Question latest = page.latestUnread();
        Optional<QuestionWindow.Day> open = c.openDay();
        LatestEvent event =
                latest != null
                        ? new LatestEvent(
                                (latest.senderUserId().equals(c.userId()) ? "answer:" : "question:")
                                        + latest.id(),
                                latest.senderUserId().equals(c.userId())
                                        ? "QUESTION_ANSWERED"
                                        : "QUESTION_RECEIVED",
                                latest.id())
                        : grantUnread && open.isPresent()
                                ? new LatestEvent(
                                        "grant:" + c.roomId() + ":" + open.get().day(),
                                        "QUESTION_AVAILABLE",
                                        null)
                                : null;
        return new QuestionListResponse(
                c.phase(),
                window.opensAt(),
                window.closesAt(),
                focus != null && focus.open() ? available(legacy) : 0,
                grantUnread,
                event != null,
                page.receivedCount(),
                event,
                List.copyOf(legacy),
                received,
                page.nextCursor(),
                focus == null ? null : focus.day(),
                List.copyOf(days));
    }

    /**
     * 상대별 상태. 보낸 상대는 질문 상태, 아직 안 보낸 상대는 {@code day}의 상태(열림·예정·마감)를 따른다.
     * {@code onlyThatDay}가 false 면 다른 날 보낸 질문도 합친다(그날 질문 → 가장 최근 질문 순). {@code day}가
     * null(일정 없는 옛 방)이면 안 보낸 상대는 마감이다.
     */
    private static List<Target> targets(
            QuestionContext c, List<Question> sent, QuestionWindow.Day day, boolean onlyThatDay) {
        Map<Long, Question> byTarget = new LinkedHashMap<>();
        sent.stream()
                .filter(q -> !onlyThatDay || day == null || q.day() == day.day())
                .sorted(
                        Comparator.comparing(
                                        (Question q) -> day != null && q.day() == day.day() ? 0 : 1)
                                .thenComparing(Question::id, Comparator.reverseOrder()))
                .forEach(q -> byTarget.putIfAbsent(q.recipientUserId(), q));
        List<Target> targets = new ArrayList<>();
        for (QuestionContext.Participant p : c.participants()) {
            if (!c.opposite(p) || c.blockedUserIds().contains(p.userId())) continue;
            Question q = byTarget.remove(p.userId());
            String status =
                    q != null
                            ? sentStatus(c, q)
                            : day == null || "CLOSED".equals(day.state())
                                    ? "CLOSED"
                                    : c.relatedUserIds().contains(p.userId())
                                            ? "UNAVAILABLE"
                                            : day.open() ? "AVAILABLE" : "LOCKED";
            targets.add(
                    new Target(
                            p.memberId(),
                            p.nickname(),
                            null,
                            q == null ? null : q.id(),
                            status,
                            q != null && q.unreadFor(c.userId())));
        }
        // Retain outgoing rows when the recipient leaves the room; their safe pseudonym is
        // snapshotted.
        for (Question q : byTarget.values())
            if (!c.blockedUserIds().contains(q.recipientUserId()))
                targets.add(
                        new Target(
                                q.recipientMemberId(),
                                q.recipientNickname(),
                                null,
                                q.id(),
                                sentStatus(c, q),
                                q.unreadFor(c.userId())));
        return List.copyOf(targets);
    }

    private static int available(List<Target> targets) {
        return (int) targets.stream().filter(t -> t.status().equals("AVAILABLE")).count();
    }

    private static String status(QuestionContext c, Question q) {
        return q.answer() != null ? "ANSWERED" : c.canInteract(q) ? "WAITING" : "CLOSED";
    }

    private static String sentStatus(QuestionContext c, Question q) {
        return q.senderReported() ? "REPORTED" : status(c, q);
    }
}
