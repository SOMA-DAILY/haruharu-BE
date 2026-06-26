package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.mission.implement.UploadSlotReader;
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
    private final UploadSlotReader uploadSlotReader;
    private final VideoPostReader videoPostReader;

    public RoomFeedService(RoomMemberReader roomMemberReader, UploadSlotReader uploadSlotReader,
                           VideoPostReader videoPostReader) {
        this.roomMemberReader = roomMemberReader;
        this.uploadSlotReader = uploadSlotReader;
        this.videoPostReader = videoPostReader;
    }

    public FeedResponse getFeed(UUID roomId, UUID userId, int day) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트

        List<UUID> slotIds = uploadSlotReader.getByDay(day).stream().map(UploadSlot::id).toList();
        if (slotIds.isEmpty()) {
            return new FeedResponse(false, List.of()); // 해당 Day 슬롯 없음 → 볼 것도 잠글 것도 없음
        }

        // 미업로드 잠금: 내가 day N에 한 건도 안 올렸으면 잠금
        if (videoPostReader.countUploaded(me.id(), slotIds) == 0) {
            return new FeedResponse(true, List.of());
        }

        List<FeedItem> posts = videoPostReader.getFeed(slotIds).stream()
                .map(p -> new FeedItem(
                        p.id(), p.roomMemberId(), p.roomMemberId().equals(me.id()),
                        p.videoUrl(), p.thumbnailUrl(), p.caption(), p.createdAt()))
                .toList();
        return new FeedResponse(false, posts);
    }
}
