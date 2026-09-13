package org.son.haruharu.domain.question.business.command;

import java.util.UUID;

public record CreateQuestionCommand(Long recipientMemberId, String content, UUID requestId) {}
