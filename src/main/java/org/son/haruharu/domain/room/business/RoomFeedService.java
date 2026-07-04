package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.dto.FeedResponse;
import org.son.haruharu.domain.room.business.dto.FeedResponse.FeedItem;
import org.son.haruharu.domain.room.business.dto.FeedResponse.SlotStatus;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** day N 영상 피드. 슬롯(시간대)별로 잠금: 내가 업로드한 슬롯의 타 멤버 영상만 열람 가능(참여 유도). */
@Service
@Transactional(readOnly = true)
public class RoomFeedService {

    private final RoomMemberReader roomMemberReader;
    private final VideoPostReader videoPostReader;

    public RoomFeedService(RoomMemberReader roomMemberReader, VideoPostReader videoPostReader) {
        this.roomMemberReader = roomMemberReader;
        this.videoPostReader = videoPostReader;
    }

    public FeedResponse getFeed(Long roomId, Long userId, int day) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId);

        List<UploadSlot> daySlots = UploadSlot.ofDay(day);
        if (daySlots.isEmpty()) {
            return new FeedResponse(List.of(), List.of()); // 슬롯 없는 Day → 볼 것도 잠글 것도 없음
        }

        Set<Integer> mySlots = Set.copyOf(videoPostReader.getUploadedSlotIndexes(me.id(), day));
        List<SlotStatus> slots = daySlots.stream()
                .map(slot -> new SlotStatus(slot.slotIndex(), !mySlots.contains(slot.slotIndex())))
                .toList();

        if (mySlots.isEmpty()) {
            return new FeedResponse(slots, List.of()); // 전 슬롯 잠금 → 피드 조회 불필요
        }

        List<Long> roomMemberIds = roomMemberReader.getByRoomId(roomId).stream()
                .map(RoomMember::id).toList();
        List<FeedItem> posts = videoPostReader.getFeed(roomMemberIds, day).stream()
                .filter(p -> mySlots.contains(p.slotIndex()))
                .map(p -> new FeedItem(
                        p.id(), p.roomMemberId(), p.roomMemberId().equals(me.id()),
                        p.slotIndex(), p.videoUrl(), p.thumbnailUrl(), p.caption(), p.createdAt()))
                .toList();
        return new FeedResponse(slots, posts);
    }
}
