package org.son.haruharu.domain.question.implement;

import java.time.LocalDateTime;

/** 질문 열림 기록. 날마다(2·3일차) 한 번씩 지급해 열림 알림과 미확인 표시를 날짜별로 둔다. */
public record QuestionGrant(
        Long id, Long roomId, Long userId, int day, boolean read, LocalDateTime createdAt) {
    public QuestionGrant markRead() {
        return new QuestionGrant(id, roomId, userId, day, true, createdAt);
    }
}
