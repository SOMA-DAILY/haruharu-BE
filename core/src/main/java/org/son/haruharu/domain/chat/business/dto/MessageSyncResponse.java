package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MessageSyncResponse(
        @Schema(description = "동기화 메시지(ID 오름차순)") List<MessagePageResponse.MessageItem> messages,
        @Schema(description = "이번 동기화에서 고정할 상한 ID") long syncTargetMessageId,
        @Schema(description = "이 응답을 로컬에 적용한 뒤 다음 요청에 넘길 after ID") long nextAfterMessageId,
        @Schema(description = "고정 상한 안에 조회할 메시지가 더 있는지") boolean hasMore
) {}
