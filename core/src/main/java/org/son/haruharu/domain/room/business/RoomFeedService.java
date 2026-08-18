package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.room.business.dto.FeedResponse;
import org.son.haruharu.domain.room.business.dto.FeedResponse.FeedItem;
import org.son.haruharu.domain.room.business.dto.FeedResponse.ReactionTally;
import org.son.haruharu.domain.room.business.dto.FeedResponse.SlotStatus;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.video.implement.ReactionCount;
import org.son.haruharu.domain.video.implement.ReactionType;
import org.son.haruharu.domain.video.implement.SlotClock;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.implement.VideoReactionReader;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 현재 노출 중인 슬롯 창(slot_start 기준 최근 3시간)의 영상 피드. 슬롯(시간창)별로 잠금:
 * 내가 업로드한 슬롯의 타 멤버 영상만 열람 가능(참여 유도). 슬롯은 방 시작 상대 정시(SlotClock).
 */
@Service
@Transactional(readOnly = true)
public class RoomFeedService {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final VideoPostReader videoPostReader;
    private final VideoReactionReader videoReactionReader;
    private final S3StoragePort s3StoragePort;
    private final Clock clock;

    public RoomFeedService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                           VideoPostReader videoPostReader, VideoReactionReader videoReactionReader,
                           S3StoragePort s3StoragePort, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.videoPostReader = videoPostReader;
        this.videoReactionReader = videoReactionReader;
        this.s3StoragePort = s3StoragePort;
        this.clock = clock;
    }

    /** 한 페이지에 실을 과거 슬롯 수 상한(#350). presigned URL 응답 폭주 방지. 기본값은 컨트롤러 defaultValue. */
    private static final int MAX_PAGE_SIZE = 24;

    /**
     * 현재 노출 창(slot_start 기준 최근 3시간)의 피드. 방 진입 시 초기 조회.
     * nextCursor는 이 창의 가장 과거 슬롯 — 클라가 그보다 과거를 이어 보려면 {@code before}로 넘긴다(방 시작이면 null).
     */
    public FeedResponse getFeed(Long roomId, Long userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId);
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);

        LocalDateTime now = LocalDateTime.now(clock);
        if (room.startsAt() == null) {
            return empty(); // 구 모델 방(slot_start 피드 없음)
        }
        // 최종선택 오픈 이후 시작하는 슬롯은 업로드가 불가능했으므로 창에서 제외한다.
        List<LocalDateTime> window = SlotClock.visibleSlotStarts(
                room.startsAt(), now, room.finalSelectionOpensAt());
        if (window.isEmpty()) {
            // 노출 슬롯은 없지만(최종선택 이후·방 종료 후 등) 과거 이력이 있으면 회고를 시작할 커서를 시드한다.
            // 안 그러면 클라가 next_cursor(=null)만 보고 과거를 열 방법이 없다(#350 리뷰). 시작 전이면 이력 없음 → null.
            return new FeedResponse(List.of(), List.of(), archiveSeedCursor(room, now));
        }
        // 촬영 CTA 대상인 현재 슬롯은 ACTIVE일 때만(오늘 화면 openNow와 동일 규칙).
        LocalDateTime currentSlotStart = room.statusAt(now) == RoomStatus.ACTIVE
                ? SlotClock.currentSlotStart(room.startsAt(), now)
                : null;
        return buildFeed(roomId, userId, me, room, window, currentSlotStart);
    }

    /**
     * {@code before} 슬롯보다 과거의 슬롯들을 최대 size개 이어 조회(과거 회고 페이지네이션, #350).
     * 노출 창(3시간)과 무관하게 방 시작까지 되짚어볼 수 있되, 게이트("내가 올린 슬롯만 남 영상 열림")는 동일 적용한다.
     * 과거 슬롯이라 촬영 CTA(current)는 항상 false다.
     */
    public FeedResponse getFeedBefore(Long roomId, Long userId, LocalDateTime before, int size) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId);
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);

        if (room.startsAt() == null) {
            return empty(); // 구 모델 방
        }
        int capped = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // before는 정상 클라이언트가 서버가 준 nextCursor를 넘기지만, 임의의 미래 시각이 와도
        // 아직 없는 미래 슬롯을 만들지 않도록 now로 상한한다(방어).
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cappedBefore = before.isAfter(now) ? now : before;
        List<LocalDateTime> window = SlotClock.pastSlotStarts(
                room.startsAt(), cappedBefore, capped, room.finalSelectionOpensAt());
        if (window.isEmpty()) {
            return empty(); // 방 시작 도달 등 더 과거 슬롯 없음
        }
        return buildFeed(roomId, userId, me, room, window, null);
    }

    private FeedResponse empty() {
        return new FeedResponse(List.of(), List.of(), null);
    }

    /**
     * 노출 창이 빈 방에서 과거 회고를 시작할 커서. 업로드 가능했던 구간의 상한(min(now, 최종선택 오픈))을 커서로 주면
     * 클라가 그걸 before로 넘겨 가장 최근 과거 슬롯부터 되짚어볼 수 있다. 상한이 방 시작 이하면 이력이 없어 null.
     */
    private LocalDateTime archiveSeedCursor(Room room, LocalDateTime now) {
        LocalDateTime cutoff = room.finalSelectionOpensAt();
        LocalDateTime upperBound = (cutoff != null && cutoff.isBefore(now)) ? cutoff : now;
        return upperBound.isAfter(room.startsAt()) ? upperBound : null;
    }

    /**
     * 주어진 슬롯 창에 게이트("내가 올린 슬롯만 남 영상 열림")를 적용해 피드를 만든다. getFeed·getFeedBefore 공용.
     * nextCursor는 창의 가장 과거 슬롯 — 그게 방 시작이면 더 과거가 없으므로 null.
     */
    private FeedResponse buildFeed(Long roomId, Long userId, RoomMember me, Room room,
                                   List<LocalDateTime> window, LocalDateTime currentSlotStart) {
        LocalDateTime oldest = window.get(0);
        LocalDateTime nextCursor = oldest.isAfter(room.startsAt()) ? oldest : null;

        Set<LocalDateTime> mySlots = Set.copyOf(videoPostReader.getUploadedSlotStarts(me.id(), window));
        List<SlotStatus> slots = window.stream()
                .map(slotStart -> new SlotStatus(slotStart, !mySlots.contains(slotStart),
                        slotStart.equals(currentSlotStart)))
                .toList();

        if (mySlots.isEmpty()) {
            return new FeedResponse(slots, List.of(), nextCursor); // 전 슬롯 잠금 → 피드 조회 불필요
        }

        // 작성자 userId(신고 연결용) 매핑을 위해 멤버 목록을 id→userId 맵으로 들고 간다
        Map<Long, Long> userIdsByMemberId = roomMemberReader.getByRoomId(roomId).stream()
                .collect(Collectors.toMap(RoomMember::id, RoomMember::userId));
        List<VideoPost> visible = videoPostReader.getFeed(List.copyOf(userIdsByMemberId.keySet()), window).stream()
                .filter(p -> mySlots.contains(p.slotStart()))
                .toList();

        List<Long> postIds = visible.stream().map(VideoPost::id).toList();
        Map<Long, List<ReactionTally>> countsByPost = aggregateCounts(videoReactionReader.getReactionCounts(postIds));
        Map<Long, String> myReactionByPost = videoReactionReader.getMyReactions(postIds, userId);

        // 비공개 영상은 재생 가능한 presigned GET URL로 변환해 내려준다(#162).
        List<FeedItem> posts = visible.stream()
                .map(p -> new FeedItem(
                        p.id(), p.roomMemberId(), userIdsByMemberId.get(p.roomMemberId()),
                        p.roomMemberId().equals(me.id()),
                        p.slotStart(),
                        s3StoragePort.createPresignedGetUrl(p.videoUrl()),
                        s3StoragePort.createPresignedGetUrl(p.thumbnailUrl()),
                        p.caption(), p.createdAt(),
                        countsByPost.getOrDefault(p.id(), List.of()),
                        myReactionByPost.get(p.id())))
                .toList();
        return new FeedResponse(slots, posts, nextCursor);
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
