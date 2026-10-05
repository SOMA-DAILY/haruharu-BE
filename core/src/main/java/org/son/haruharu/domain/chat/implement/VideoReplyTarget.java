package org.son.haruharu.domain.chat.implement;

import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.video.implement.VideoPost;

/**
 * 답장 대상 영상과 그 작성자, 답장·미리보기 가능 여부를 한 번에 판정한 결과.
 * 전송은 {@link Reason}별 예외로, 미리보기는 {@link #state()}로 같은 판정을 사용한다.
 */
public record VideoReplyTarget(VideoPost post, RoomMember owner, Reason reason) {

    public enum Reason {
        AVAILABLE,
        DELETED,
        HIDDEN,
        BLOCKED,
        OWNER_WITHDRAWN,
        OWNER_LEFT
    }

    public static VideoReplyTarget deleted() {
        return new VideoReplyTarget(null, null, Reason.DELETED);
    }

    /** 영상 자체를 볼 수 없는 상태(삭제·숨김). 전송 시 404로 응답한다. */
    public boolean isHiddenOrDeleted() {
        return reason == Reason.DELETED || reason == Reason.HIDDEN;
    }

    public boolean isOwnedBy(Long userId) {
        return owner != null && owner.userId().equals(userId);
    }

    public VideoReplyState state() {
        return switch (reason) {
            case AVAILABLE -> VideoReplyState.AVAILABLE;
            case DELETED -> VideoReplyState.DELETED;
            case HIDDEN, BLOCKED, OWNER_WITHDRAWN, OWNER_LEFT -> VideoReplyState.UNAVAILABLE;
        };
    }
}
