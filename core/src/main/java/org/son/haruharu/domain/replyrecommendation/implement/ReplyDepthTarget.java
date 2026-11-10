package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;

/**
 * 라벨을 만들 실제 영상 답장(ai_reply.reply_link). 추천 출처는 유효할 때만 담는다.
 *
 * @param provenance 연결된 노출(episode)의 출처 원래 값(REAL_USER·QA·SYNTHETIC 등). 노출 없이 보낸 답장이면 null.
 *                   모르는 값이어도 그대로 옮겨 적어 한 답장 때문에 수집이 멈추지 않게 한다.
 */
public record ReplyDepthTarget(Long messageId, Long videoId, Long viewerId, Instant sentAt, String sourceSetId,
                               String sourceCardId, String provenance) {}
