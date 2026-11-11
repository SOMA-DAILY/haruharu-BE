package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;

/**
 * ai_reply.reply_depth_observation 한 행. 원문 텍스트는 없다. provenance 는 출처 값 그대로(REAL_USER·QA 등).
 *
 * @param ownerReplied         보조 지표. 관찰 기간 안에 영상 작성자가 이 답장의 답글 트리 안에서 답했는지. 미관측이거나
 *                             작성자를 모르면 null.
 * @param followupMessageCount 보조 지표. 관찰 기간 안에 같은 채팅방에서 답장한 사람과 영상 작성자가 보낸 메시지 수
 *                             (답글 연결과 무관, 답장 자체·숨겨진 메시지 제외). 답장 메시지를 못 찾거나 작성자를 모르면 null.
 * @param response             효과 라벨(이슈 #560). 561 SQL 을 적용하지 않았거나 계산하지 않았으면 {@link ReplyResponseLabel#NONE}.
 * @param cohortId             답장 채팅방의 3일 방 기수(rooms.cohort_id). 학습 분할(최신 기수 = test)에 쓴다.
 *                             답장 메시지를 못 찾았거나 기수 없는 방이면 null.
 */
public record ReplyDepthObservation(
        Long messageId,
        Long videoId,
        Long viewerId,
        Long roomId,
        String sourceSetId,
        String sourceCardId,
        ReplyDepthResult result,
        Instant windowStart,
        Instant windowEnd,
        Instant observedAt,
        String provenance,
        Boolean ownerReplied,
        Integer followupMessageCount,
        Long cohortId,
        ReplyResponseLabel response
) {

    public ReplyDepthObservation {
        if (response == null) {
            response = ReplyResponseLabel.NONE;
        }
    }

    /** 효과 라벨 없는 모양(이슈 #560 이전). */
    public ReplyDepthObservation(Long messageId, Long videoId, Long viewerId, Long roomId, String sourceSetId,
                                 String sourceCardId, ReplyDepthResult result, Instant windowStart, Instant windowEnd,
                                 Instant observedAt, String provenance, Boolean ownerReplied,
                                 Integer followupMessageCount, Long cohortId) {
        this(messageId, videoId, viewerId, roomId, sourceSetId, sourceCardId, result, windowStart, windowEnd,
                observedAt, provenance, ownerReplied, followupMessageCount, cohortId, ReplyResponseLabel.NONE);
    }
}
