package org.son.haruharu.domain.question.implement;

import org.son.haruharu.domain.question.implement.exception.QuestionAccessDeniedException;
import org.son.haruharu.domain.question.implement.exception.QuestionUnavailableException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record QuestionContext(
        Long roomId,
        Long userId,
        Long memberId,
        String gender,
        String phase,
        LocalDateTime opensAt,
        LocalDateTime closesAt,
        List<Participant> participants,
        Set<Long> blockedUserIds,
        Set<Long> relatedUserIds) {
    public record Participant(Long memberId, Long userId, String gender, String nickname) {
        public boolean eligibleForGrant(Set<Long> active) {
            return active.contains(userId) && ("MALE".equals(gender) || "FEMALE".equals(gender));
        }
    }

    public static QuestionContext forUser(
            Long roomId,
            Long userId,
            QuestionWindow window,
            List<Participant> all,
            Set<Long> active,
            Set<Long> blocked,
            Set<Long> related) {
        List<Participant> participants =
                all.stream().filter(p -> active.contains(p.userId())).toList();
        Participant me =
                participants.stream()
                        .filter(p -> p.userId().equals(userId))
                        .findFirst()
                        .orElseThrow(QuestionAccessDeniedException::new);
        return new QuestionContext(
                roomId,
                userId,
                me.memberId(),
                me.gender(),
                window.phase(),
                window.opensAt(),
                window.closesAt(),
                participants,
                blocked,
                related);
    }

    public boolean open() {
        return phase.equals("OPEN");
    }

    public boolean eligibleGender() {
        return "MALE".equals(gender) || "FEMALE".equals(gender);
    }

    public boolean opposite(Participant p) {
        return eligibleGender()
                && p.gender() != null
                && !gender.equals(p.gender())
                && ("MALE".equals(p.gender()) || "FEMALE".equals(p.gender()));
    }

    public boolean present(Long userId) {
        return participants.stream().anyMatch(p -> p.userId().equals(userId));
    }

    public boolean canInteract(Question q) {
        return open()
                && present(q.otherUserId(userId))
                && !relatedUserIds.contains(q.otherUserId(userId))
                && !q.senderReported()
                && !q.recipientReported();
    }

    public boolean visible(Question q) {
        return !q.hiddenFor(userId) && !blockedUserIds.contains(q.otherUserId(userId));
    }

    public Participant target(Long memberId) {
        return participants.stream()
                .filter(p -> p.memberId().equals(memberId))
                .findFirst()
                .orElseThrow(QuestionUnavailableException::new);
    }

    public void validateTarget(Participant target) {
        if (!open() || !opposite(target) || relatedUserIds.contains(target.userId()))
            throw new QuestionUnavailableException();
    }
}
