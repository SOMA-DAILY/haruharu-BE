package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;

/** 저장된 실제 영상 답장과 추천 출처의 연결. */
public record ReplyLink(
        Long messageId,
        Long viewerId,
        Long videoId,
        Instant sentAt,
        String sourceSetId,
        String sourceCardId,
        String sourceSessionId,
        ReplySourceStatus sourceStatus,
        String invalidReason,
        ReplyAttribution attribution,
        Long episodeId
) {}
