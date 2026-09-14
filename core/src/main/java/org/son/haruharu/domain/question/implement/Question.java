package org.son.haruharu.domain.question.implement;

import org.son.haruharu.domain.question.implement.exception.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Sender identifiers stay internal and are never serialized by the API. */
public record Question(
        Long id,
        Long roomId,
        Long senderMemberId,
        Long recipientMemberId,
        Long senderUserId,
        Long recipientUserId,
        String recipientNickname,
        String content,
        UUID requestId,
        String answer,
        LocalDateTime createdAt,
        LocalDateTime answeredAt,
        boolean recipientRead,
        boolean senderRead,
        boolean senderReported,
        boolean recipientReported) {
    public static Question create(
            Long roomId,
            Long senderMemberId,
            Long recipientMemberId,
            Long senderUserId,
            Long recipientUserId,
            String recipientNickname,
            String content,
            UUID requestId,
            LocalDateTime now) {
        if (requestId == null) throw new InvalidQuestionContentException();
        return new Question(
                null,
                roomId,
                senderMemberId,
                recipientMemberId,
                senderUserId,
                recipientUserId,
                recipientNickname,
                validate(content, 50),
                requestId,
                null,
                now,
                null,
                false,
                true,
                false,
                false);
    }

    public static String validate(String value, int maximum) {
        if (value == null) throw new InvalidQuestionContentException();
        int start = 0, end = value.length();
        while (start < end && whitespace(value.codePointAt(start)))
            start += Character.charCount(value.codePointAt(start));
        while (end > start && whitespace(value.codePointBefore(end)))
            end -= Character.charCount(value.codePointBefore(end));
        String normalized = value.substring(start, end);
        if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > maximum)
            throw new InvalidQuestionContentException();
        return normalized;
    }

    private static boolean whitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0xFEFF;
    }

    public void validateReport(Long userId, boolean validReason, String detail) {
        if (senderUserId.equals(userId) && answer == null) throw new QuestionUnavailableException();
        if (!validReason || detail != null && detail.length() > 500)
            throw new InvalidQuestionContentException();
    }

    public Question answer(String content, LocalDateTime now) {
        String normalized = validate(content, 100);
        if (answer != null) {
            if (answer.equals(normalized)) return this;
            throw new QuestionAlreadyAnsweredException();
        }
        if (senderReported || recipientReported) throw new QuestionUnavailableException();
        return copy(normalized, now, true, false, senderReported, recipientReported);
    }

    public boolean participant(Long userId) {
        return senderUserId.equals(userId) || recipientUserId.equals(userId);
    }

    public Long otherUserId(Long userId) {
        return senderUserId.equals(userId) ? recipientUserId : senderUserId;
    }

    public boolean hiddenFor(Long userId) {
        return senderUserId.equals(userId) ? senderReported : recipientReported;
    }

    public boolean unreadFor(Long userId) {
        return !hiddenFor(userId)
                && (senderUserId.equals(userId) ? answer != null && !senderRead : !recipientRead);
    }

    public Question readBy(Long userId) {
        return copy(
                answer,
                answeredAt,
                recipientRead || recipientUserId.equals(userId),
                senderRead || senderUserId.equals(userId),
                senderReported,
                recipientReported);
    }

    public Question reportedBy(Long userId) {
        return copy(
                answer,
                answeredAt,
                recipientRead,
                senderRead,
                senderReported || senderUserId.equals(userId),
                recipientReported || recipientUserId.equals(userId));
    }

    private Question copy(
            String answer,
            LocalDateTime answeredAt,
            boolean recipientRead,
            boolean senderRead,
            boolean senderReported,
            boolean recipientReported) {
        return new Question(
                id,
                roomId,
                senderMemberId,
                recipientMemberId,
                senderUserId,
                recipientUserId,
                recipientNickname,
                content,
                requestId,
                answer,
                createdAt,
                answeredAt,
                recipientRead,
                senderRead,
                senderReported,
                recipientReported);
    }
}
