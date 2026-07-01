package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.dto.FeedResponse;
import org.son.haruharu.domain.room.business.dto.FeedResponse.FeedItem;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** day N 영상 피드. 내가 그 Day에 업로드하지 않았으면 타 멤버 콘텐츠를 잠근다(참여 유도). */
@Service
@Transactional(readOnly = true)
public class RoomFeedService {

    private final RoomMemberReader roomMemberReader;
    private final VideoPostReader videoPostReader;

    public RoomFeedService(RoomMemberReader roomMemberReader, VideoPostReader videoPostReader) {
        this.roomMemberReader = roomMemberReader;
        this.videoPostReader = videoPostReader;
    }

    public FeedResponse getFeed(UUID roomId, UUID userId, int day) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId);

        if (UploadSlot.ofDay(day).isEmpty()) {
            return new FeedResponse(false, List.of()); // 슬롯 없는 Day → 볼 것도 잠글 것도 없음
        }

        // 미업로드 잠금: 내가 day N에 한 건도 안 올렸으면 잠금
        if (videoPostReader.countUploaded(me.id(), day) == 0) {
            return new FeedResponse(true, List.of());
        }

        // 피드는 이 방 멤버들의 영상으로 한정(roomMemberId가 방을 특정)
        List<UUID> roomMemberIds = roomMemberReader.getByRoomId(roomId).stream()
                .map(RoomMember::id).toList();
        List<FeedItem> posts = videoPostReader.getFeed(roomMemberIds, day).stream()
                .map(p -> new FeedItem(
                        p.id(), p.roomMemberId(), p.roomMemberId().equals(me.id()),
                        p.videoUrl(), p.thumbnailUrl(), p.caption(), p.createdAt()))
                .toList();
        return new FeedResponse(false, posts);
    }
}
