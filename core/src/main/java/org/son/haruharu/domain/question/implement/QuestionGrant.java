package org.son.haruharu.domain.question.implement;

import java.time.LocalDateTime;

public record QuestionGrant(
        Long id, Long roomId, Long userId, boolean read, LocalDateTime createdAt) {
    public QuestionGrant markRead() {
        return new QuestionGrant(id, roomId, userId, true, createdAt);
    }
}
