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
        Long episodeId,
        /** 보낸 사람의 비교 그룹(이슈 #560). 561 SQL 을 적용하지 않았으면 null 이고 칸을 쓰지 않는다. */
        ExperimentArm experimentArm
) {

    /** 비교 그룹 없는 모양(이슈 #560 이전). */
    public ReplyLink(Long messageId, Long viewerId, Long videoId, Instant sentAt, String sourceSetId,
                     String sourceCardId, String sourceSessionId, ReplySourceStatus sourceStatus, String invalidReason,
                     ReplyAttribution attribution, Long episodeId) {
        this(messageId, viewerId, videoId, sentAt, sourceSetId, sourceCardId, sourceSessionId, sourceStatus,
                invalidReason, attribution, episodeId, null);
    }
}
