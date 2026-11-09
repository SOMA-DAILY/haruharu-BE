package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.LocalDateTime;

/** 새로 저장된 영상 답장과, 앱이 함께 보낸 추천 출처(검증 전). 본문은 담지 않는다. */
public record SavedVideoReply(
        Long messageId,
        Long viewerId,
        Long videoId,
        LocalDateTime sentAt,
        String sourceSetId,
        String sourceCardId,
        String sourceSessionId
) {
    public boolean hasSource() {
        return sourceSetId != null && !sourceSetId.isBlank();
    }
}
