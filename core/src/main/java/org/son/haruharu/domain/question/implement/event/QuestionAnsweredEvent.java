package org.son.haruharu.domain.question.implement.event;

public record QuestionAnsweredEvent(
        Long roomId, Long questionId, Long senderUserId, Long recipientUserId) {}
