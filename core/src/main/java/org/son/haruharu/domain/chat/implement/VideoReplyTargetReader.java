package org.son.haruharu.domain.chat.implement;

import lombok.RequiredArgsConstructor;

import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.implement.VideoReplyTarget.Reason;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.storage.exception.VideoPostNotFoundException;
import org.springframework.stereotype.Component;

/** 답장 대상 영상을 조회하고 viewer 기준으로 답장·미리보기 가능 여부를 판정한다. */
@Component
@RequiredArgsConstructor
public class VideoReplyTargetReader {
    private final VideoPostReader videoPostReader;
    private final RoomMemberReader roomMemberReader;
    private final ChatRoomMemberReader chatRoomMemberReader;
    private final BlockReader blockReader;
    private final UserReader userReader;

    /** 다른 방의 영상이면 존재를 드러내지 않도록 404를 던진다. */
    public VideoReplyTarget get(Long roomId, Long chatRoomId, Long videoPostId, Long viewerId) {
        VideoPost post;
        try {
            post = videoPostReader.getById(videoPostId);
        } catch (VideoPostNotFoundException e) {
            return VideoReplyTarget.deleted();
        }
        RoomMember owner = roomMemberReader.getById(post.roomMemberId());
        if (!owner.roomId().equals(roomId)) throw new VideoPostNotFoundException();
        return new VideoReplyTarget(post, owner, judge(post, owner, chatRoomId, viewerId));
    }

    private Reason judge(VideoPost post, RoomMember owner, Long chatRoomId, Long viewerId) {
        if (post.deletedAt() != null) return Reason.DELETED;
        if (post.isHidden()) return Reason.HIDDEN;
        if (blockReader.isBlockedBetween(viewerId, owner.userId())) return Reason.BLOCKED;
        if (userReader.getById(owner.userId()).status() == UserStatus.DELETED)
            return Reason.OWNER_WITHDRAWN;
        if (chatRoomMemberReader.findActiveMembership(chatRoomId, owner.userId()).isEmpty())
            return Reason.OWNER_LEFT;
        return Reason.AVAILABLE;
    }
}
