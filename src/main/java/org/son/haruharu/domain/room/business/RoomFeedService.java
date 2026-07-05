package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.dto.FeedResponse;
import org.son.haruharu.domain.room.business.dto.FeedResponse.FeedItem;
import org.son.haruharu.domain.room.business.dto.FeedResponse.ReactionCounts;
import org.son.haruharu.domain.room.business.dto.FeedResponse.SlotStatus;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.video.implement.ReactionCount;
import org.son.haruharu.domain.video.implement.ReactionType;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.implement.VideoReactionReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** day N 영상 피드. 슬롯(시간대)별로 잠금: 내가 업로드한 슬롯의 타 멤버 영상만 열람 가능(참여 유도). */
@Service
@Transactional(readOnly = true)
public class RoomFeedService {

    private final RoomMemberReader roomMemberReader;
    private final VideoPostReader videoPostReader;
    private final VideoReactionReader videoReactionReader;

    public RoomFeedService(RoomMemberReader roomMemberReader, VideoPostReader videoPostReader,
                           VideoReactionReader videoReactionReader) {
        this.roomMemberReader = roomMemberReader;
        this.videoPostReader = videoPostReader;
        this.videoReactionReader = videoReactionReader;
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
        List<VideoPost> visible = videoPostReader.getFeed(roomMemberIds, day).stream()
                .filter(p -> mySlots.contains(p.slotIndex()))
                .toList();

        List<Long> postIds = visible.stream().map(VideoPost::id).toList();
        Map<Long, ReactionCounts> countsByPost = aggregateCounts(videoReactionReader.getReactionCounts(postIds));
        Map<Long, String> myReactionByPost = videoReactionReader.getMyReactions(postIds, userId);

        List<FeedItem> posts = visible.stream()
                .map(p -> new FeedItem(
                        p.id(), p.roomMemberId(), p.roomMemberId().equals(me.id()),
                        p.slotIndex(), p.videoUrl(), p.thumbnailUrl(), p.caption(), p.createdAt(),
                        countsByPost.getOrDefault(p.id(), ReactionCounts.zero()),
                        myReactionByPost.get(p.id())))
                .toList();
        return new FeedResponse(slots, posts);
    }

    private Map<Long, ReactionCounts> aggregateCounts(List<ReactionCount> rows) {
        Map<Long, long[]> acc = new HashMap<>(); // [like, curious, empathy]
        for (ReactionCount row : rows) {
            ReactionType type = ReactionType.parse(row.reactionType());
            if (type == null) {
                continue; // DB에 저장된 알 수 없는 값 방어(토글 API가 enum으로 제한)
            }
            long[] c = acc.computeIfAbsent(row.videoPostId(), k -> new long[3]);
            switch (type) {
                case LIKE -> c[0] = row.count();
                case CURIOUS -> c[1] = row.count();
                case EMPATHY -> c[2] = row.count();
            }
        }
        Map<Long, ReactionCounts> result = new HashMap<>();
        acc.forEach((postId, c) -> result.put(postId, new ReactionCounts(c[0], c[1], c[2])));
        return result;
    }
}
