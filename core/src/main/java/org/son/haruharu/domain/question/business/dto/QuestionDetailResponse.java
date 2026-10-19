package org.son.haruharu.domain.question.business.dto;

import org.son.haruharu.domain.question.implement.Question;
import org.son.haruharu.domain.question.implement.QuestionContext;

import java.time.LocalDateTime;

public record QuestionDetailResponse(
        Long id,
        String question,
        String answer,
        String status,
        boolean canAnswer,
        String direction,
        Recipient recipient,
        LocalDateTime createdAt,
        LocalDateTime answeredAt,
        int day) {
    public record Recipient(Long roomMemberId, String nickname, String avatarUrl) {}

    public static QuestionDetailResponse from(Question q, QuestionContext context) {
        boolean sent = q.senderUserId().equals(context.userId());
        boolean interactive = context.canInteract(q);
        return new QuestionDetailResponse(
                q.id(),
                q.content(),
                q.answer(),
                q.answer() != null ? "ANSWERED" : interactive ? "WAITING" : "CLOSED",
                !sent && q.answer() == null && interactive,
                sent ? "SENT" : "RECEIVED",
                sent ? new Recipient(q.recipientMemberId(), q.recipientNickname(), null) : null,
                q.createdAt(),
                q.answeredAt(),
                q.day());
    }
}
