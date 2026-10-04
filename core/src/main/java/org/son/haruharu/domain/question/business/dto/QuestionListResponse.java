package org.son.haruharu.domain.question.business.dto;

import org.son.haruharu.domain.question.implement.*;

import java.time.LocalDateTime;
import java.util.*;

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
        Long receivedNextCursor) {
    public record Target(
            Long roomMemberId,
            String nickname,
            String avatarUrl,
            Long questionId,
            String status,
            boolean unread) {}

    public record Received(Long id, String status, boolean unread, LocalDateTime createdAt) {}

    public record LatestEvent(String key, String type, Long questionId) {}

    public static QuestionListResponse from(
            QuestionContext c, QuestionReader.Page page, boolean grantUnread) {
        List<Question> questions = page.questions();
        List<Question> sent =
                questions.stream().filter(q -> q.senderUserId().equals(c.userId())).toList();
        Map<Long, Question> byTarget = new LinkedHashMap<>();
        sent.forEach(q -> byTarget.put(q.recipientUserId(), q));
        List<Target> targets = new ArrayList<>();
        for (QuestionContext.Participant p : c.participants()) {
            if (!c.opposite(p) || c.blockedUserIds().contains(p.userId())) continue;
            Question q = byTarget.remove(p.userId());
            String status =
                    q != null
                            ? sentStatus(c, q)
                            : !c.open()
                                    ? "CLOSED"
                                    : c.relatedUserIds().contains(p.userId())
                                            ? "UNAVAILABLE"
                                            : "AVAILABLE";
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
                                                q.createdAt()))
                        .toList();
        Question latest = page.latestUnread();
        LatestEvent event =
                latest != null
                        ? new LatestEvent(
                                (latest.senderUserId().equals(c.userId()) ? "answer:" : "question:")
                                        + latest.id(),
                                latest.senderUserId().equals(c.userId())
                                        ? "QUESTION_ANSWERED"
                                        : "QUESTION_RECEIVED",
                                latest.id())
                        : grantUnread
                                ? new LatestEvent("grant:" + c.roomId(), "QUESTION_AVAILABLE", null)
                                : null;
        return new QuestionListResponse(
                c.phase(),
                c.opensAt(),
                c.closesAt(),
                (int) targets.stream().filter(t -> t.status().equals("AVAILABLE")).count(),
                grantUnread,
                event != null,
                page.receivedCount(),
                event,
                List.copyOf(targets),
                received,
                page.nextCursor());
    }

    private static String status(QuestionContext c, Question q) {
        return q.answer() != null ? "ANSWERED" : c.canInteract(q) ? "WAITING" : "CLOSED";
    }

    private static String sentStatus(QuestionContext c, Question q) {
        return q.senderReported() ? "REPORTED" : status(c, q);
    }
}
