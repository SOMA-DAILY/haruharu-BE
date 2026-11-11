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
        String sourceSessionId,
        /** 답장한 3일 방(비교 그룹 판단용, 이슈 #560). 모르면 null. */
        Long roomId
) {

    /** 방 없는 모양(이슈 #560 이전). */
    public SavedVideoReply(Long messageId, Long viewerId, Long videoId, LocalDateTime sentAt, String sourceSetId,
                           String sourceCardId, String sourceSessionId) {
        this(messageId, viewerId, videoId, sentAt, sourceSetId, sourceCardId, sourceSessionId, null);
    }

    public boolean hasSource() {
        return sourceSetId != null && !sourceSetId.isBlank();
    }
}
