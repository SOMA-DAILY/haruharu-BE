package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;

/** 노출 한 번(세션·세트·시청자·영상). */
public record ReplyEpisode(
        Long id,
        String sessionId,
        String setId,
        Long viewerId,
        Long videoId,
        Instant firstImpressionAt,
        Instant lastImpressionAt,
        Provenance provenance
) {}
