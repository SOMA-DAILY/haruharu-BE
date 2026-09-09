package org.son.haruharu.domain.block.application.dto;

import jakarta.validation.constraints.NotNull;
import org.son.haruharu.domain.block.business.command.CreateBlockCommand;

/** 차단 등록 요청. 전역 SNAKE_CASE 역직렬화(blocked_user_id). */
public record CreateBlockRequest(@NotNull Long blockedUserId) {

    public CreateBlockCommand toCommand() {
        return new CreateBlockCommand(blockedUserId);
    }
}
