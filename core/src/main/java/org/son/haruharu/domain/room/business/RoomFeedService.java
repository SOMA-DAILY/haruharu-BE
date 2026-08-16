package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.dto.FeedResponse;
import org.son.haruharu.domain.room.business.dto.FeedResponse.FeedItem;
import org.son.haruharu.domain.room.business.dto.FeedResponse.ReactionTally;
import org.son.haruharu.domain.room.business.dto.FeedResponse.SlotStatus;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.video.implement.ReactionCount;
import org.son.haruharu.domain.video.implement.ReactionType;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.implement.VideoReactionReader;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** day N 영상 피드. 슬롯(시간대)별로 잠금: 내가 업로드한 슬롯의 타 멤버 영상만 열람 가능(참여 유도). */
@Service
@Transactional(readOnly = true)
public class RoomFeedService {

    private final RoomMemberReader roomMemberReader;
    private final VideoPostReader videoPostReader;
    private final VideoReactionReader videoReactionReader;
    private final S3StoragePort s3StoragePort;

    public RoomFeedService(RoomMemberReader roomMemberReader, VideoPostReader videoPostReader,
                           VideoReactionReader videoReactionReader, S3StoragePort s3StoragePort) {
        this.roomMemberReader = roomMemberReader;
        this.videoPostReader = videoPostReader;
        this.videoReactionReader = videoReactionReader;
        this.s3StoragePort = s3StoragePort;
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

        // 작성자 userId(신고 연결용) 매핑을 위해 멤버 목록을 id→userId 맵으로 들고 간다
        Map<Long, Long> userIdsByMemberId = roomMemberReader.getByRoomId(roomId).stream()
                .collect(Collectors.toMap(RoomMember::id, RoomMember::userId));
        List<VideoPost> visible = videoPostReader.getFeed(List.copyOf(userIdsByMemberId.keySet()), day).stream()
                .filter(p -> mySlots.contains(p.slotIndex()))
                .toList();

        List<Long> postIds = visible.stream().map(VideoPost::id).toList();
        Map<Long, List<ReactionTally>> countsByPost = aggregateCounts(videoReactionReader.getReactionCounts(postIds));
        Map<Long, String> myReactionByPost = videoReactionReader.getMyReactions(postIds, userId);

        // 비공개 영상은 재생 가능한 presigned GET URL로 변환해 내려준다(#162).
        List<FeedItem> posts = visible.stream()
                .map(p -> new FeedItem(
                        p.id(), p.roomMemberId(), userIdsByMemberId.get(p.roomMemberId()),
                        p.roomMemberId().equals(me.id()),
                        p.slotIndex(),
                        s3StoragePort.createPresignedGetUrl(p.videoUrl()),
                        s3StoragePort.createPresignedGetUrl(p.thumbnailUrl()),
                        p.caption(), p.createdAt(),
                        countsByPost.getOrDefault(p.id(), List.of()),
                        myReactionByPost.get(p.id())))
                .toList();
        return new FeedResponse(slots, posts);
    }

    /**
     * 영상별 반응 집계를 이모지 타입별 리스트로 변환한다. 0인 타입은 담지 않고, EnumMap으로 모아 enum 선언 순서로 정렬한다.
     * 고정 이모지 세트라 타입이 늘어도(집계 배열 인덱스 없이) 그대로 확장된다.
     */
    private Map<Long, List<ReactionTally>> aggregateCounts(List<ReactionCount> rows) {
        Map<Long, EnumMap<ReactionType, Long>> acc = new HashMap<>();
        for (ReactionCount row : rows) {
            ReactionType type = ReactionType.parse(row.reactionType());
            if (type == null) {
                continue; // DB에 저장된 알 수 없는 값 방어(토글 API가 enum으로 제한)
            }
            acc.computeIfAbsent(row.videoPostId(), k -> new EnumMap<>(ReactionType.class))
                    .put(type, row.count());
        }
        Map<Long, List<ReactionTally>> result = new HashMap<>();
        acc.forEach((postId, byType) -> {
            List<ReactionTally> tallies = byType.entrySet().stream()
                    .map(e -> new ReactionTally(e.getKey().name(), e.getKey().emoji(), e.getValue()))
                    .toList();
            result.put(postId, tallies);
        });
        return result;
    }
}
