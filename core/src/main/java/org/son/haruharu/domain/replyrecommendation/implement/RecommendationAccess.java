package org.son.haruharu.domain.replyrecommendation.implement;

import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.video.implement.VideoPost;

/**
 * 시청자가 이 영상의 추천을 받을 수 있는지. denial 은 요청 자체를 거절할 이유(403·404 로 바뀐다),
 * reason 은 카드 없이 UNAVAILABLE 로 답할 이유다. 둘 다 없으면 허용이다.
 */
public record RecommendationAccess(
        Denial denial,
        RecommendationReason reason,
        VideoPost post,
        RoomMember owner,
        Long chatRoomId,
        AccessScope scope
) {
    public enum Denial { NOT_MEMBER, ROOM_NOT_FOUND }

    public static RecommendationAccess denied(Denial denial) {
        return new RecommendationAccess(denial, null, null, null, null, null);
    }

    public static RecommendationAccess unavailable(RecommendationReason reason) {
        return new RecommendationAccess(null, reason, null, null, null, null);
    }

    public boolean allowed() {
        return denial == null && reason == null;
    }
}
