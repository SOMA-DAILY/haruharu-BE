package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.dto.FeedResponse;
import org.son.haruharu.domain.room.business.dto.FeedResponse.FeedItem;
import org.son.haruharu.domain.room.business.dto.FeedResponse.SlotStatus;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.video.implement.ReactionCount;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.implement.VideoReactionReader;
import org.son.haruharu.infrastructure.s3.S3StoragePort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RoomFeedServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate DATE = LocalDate.of(2026, 6, 26);
    private static final LocalDateTime STARTS = DATE.atTime(20, 0);
    // now=22:10 기준 노출 창 = [20:00, 21:00, 22:00]
    private static final LocalDateTime S20 = DATE.atTime(20, 0);
    private static final LocalDateTime S21 = DATE.atTime(21, 0);
    private static final LocalDateTime S22 = DATE.atTime(22, 0);

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock VideoPostReader videoPostReader;
    @Mock VideoReactionReader videoReactionReader;
    @Mock S3StoragePort s3StoragePort;

    RoomFeedService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(ZonedDateTime.of(2026, 6, 26, 22, 10, 0, 0, SEOUL).toInstant(), SEOUL);
        service = new RoomFeedService(roomReader, roomMemberReader, videoPostReader,
                videoReactionReader, s3StoragePort, clock);
        // 비공개 영상 URL은 presigned GET URL로 서명해 응답한다(#162). 서명 적용 여부 관찰용 접두사.
        lenient().when(s3StoragePort.createPresignedGetUrl(any()))
                .thenAnswer(inv -> inv.getArgument(0) == null ? null : "signed:" + inv.getArgument(0));
    }

    private Room activeRoom() {
        return new Room(10L, RoomStatus.ACTIVE, DATE, 3, STARTS, null,
                STARTS, STARTS.plusDays(2), STARTS.plusDays(2).plusHours(2),
                STARTS.plusDays(2).plusHours(3), STARTS.plusDays(3));
    }

    private RoomMember member(Long id, Long userId) {
        return new RoomMember(id, 10L, userId, "MALE", null, LocalDateTime.now());
    }

    private VideoPost post(Long postId, Long memberId, LocalDateTime slotStart, String url) {
        return new VideoPost(postId, memberId, slotStart, null, null, url, null, false, null, null,
                LocalDateTime.now());
    }

    private boolean lockedOf(FeedResponse r, LocalDateTime slotStart) {
        return r.slots().stream().filter(s -> s.slotStart().equals(slotStart))
                .map(SlotStatus::locked).findFirst().orElseThrow();
    }

    private boolean currentOf(FeedResponse r, LocalDateTime slotStart) {
        return r.slots().stream().filter(s -> s.slotStart().equals(slotStart))
                .map(SlotStatus::current).findFirst().orElseThrow();
    }

    private FeedItem itemOf(FeedResponse r, Long postId) {
        return r.posts().stream().filter(p -> p.videoPostId().equals(postId)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("요청자가 올리지 않아도 타 멤버의 공개 영상과 해당 슬롯을 반환한다")
    void givenOtherMemberPost_whenFeed_thenReturnsUnlockedPostAndSlot() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        given(roomReader.findById(10L)).willReturn(Optional.of(activeRoom()));
        given(roomMemberReader.getByRoomId(10L)).willReturn(List.of(member(20L, 1L), member(30L, 2L)));
        given(videoPostReader.getFeed(List.of(20L, 30L), List.of(S20, S21, S22)))
                .willReturn(List.of(post(101L, 30L, S21, "other-s21")));
        given(videoReactionReader.getReactionCounts(List.of(101L))).willReturn(List.of());
        given(videoReactionReader.getMyReactions(List.of(101L), 1L)).willReturn(Map.of());

        FeedResponse result = service.getFeed(10L, 1L);

        assertThat(result.slots()).extracting(SlotStatus::slotStart).containsExactly(S21, S22);
        assertThat(result.slots()).noneMatch(SlotStatus::locked);
        assertThat(result.posts()).extracting(FeedItem::videoPostId).containsExactly(101L);
    }

    @Test
    @DisplayName("모든 슬롯이 비어도 빈 현재 슬롯은 유지하고 잠그지 않는다")
    void givenEmptyCurrentWindow_whenFeed_thenKeepsOnlyCurrentUnlocked() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        given(roomReader.findById(10L)).willReturn(Optional.of(activeRoom()));
        given(roomMemberReader.getByRoomId(10L)).willReturn(List.of(member(20L, 1L), member(30L, 2L)));
        given(videoPostReader.getFeed(List.of(20L, 30L), List.of(S20, S21, S22))).willReturn(List.of());

        FeedResponse result = service.getFeed(10L, 1L);

        assertThat(result.slots()).extracting(SlotStatus::slotStart).containsExactly(S22);
        assertThat(result.slots()).allMatch(slot -> !slot.locked());
        assertThat(result.posts()).isEmpty();
    }

    @Test
    @DisplayName("공개 영상은 모든 슬롯에 반환되고 반응 집계와 presigned URL을 유지한다")
    void givenVisiblePosts_whenFeed_thenReturnsPostsWithReactionsAndSignedUrls() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        given(roomReader.findById(10L)).willReturn(Optional.of(activeRoom()));
        given(roomMemberReader.getByRoomId(10L))
                .willReturn(List.of(member(20L, 1L), member(30L, 2L)));
        given(videoPostReader.getFeed(anyList(), anyList())).willReturn(List.of(
                post(100L, 20L, S21, "my-s21"),
                post(101L, 30L, S21, "other-s21"),
                post(102L, 30L, S22, "other-s22"),
                post(103L, 30L, S20, "other-s20")
        ));
        given(videoReactionReader.getReactionCounts(anyList())).willReturn(List.of(
                new ReactionCount(101L, "LIKE", 3),
                new ReactionCount(101L, "HEART", 1)
        ));
        given(videoReactionReader.getMyReactions(anyList(), eq(1L))).willReturn(Map.of(101L, "LIKE"));

        FeedResponse result = service.getFeed(10L, 1L);

        assertThat(result.slots()).allMatch(slot -> !slot.locked());
        assertThat(result.posts()).extracting(FeedItem::videoPostId).containsExactly(100L, 101L, 102L, 103L);
        assertThat(result.posts()).extracting(FeedItem::slotStart).containsExactly(S21, S21, S22, S20);
        assertThat(result.posts()).extracting(FeedItem::mine).containsExactly(true, false, false, false);
        assertThat(result.posts()).extracting(FeedItem::userId).containsExactly(1L, 2L, 2L, 2L);
        assertThat(itemOf(result, 101L).videoUrl()).isEqualTo("signed:other-s21");

        // post 101: 집계(0 생략·enum 순서) + 내 반응
        FeedItem p101 = itemOf(result, 101L);
        assertThat(p101.reactionCounts()).extracting(
                FeedResponse.ReactionTally::type,
                FeedResponse.ReactionTally::emoji,
                FeedResponse.ReactionTally::count)
                .containsExactly(tuple("LIKE", "👍", 3L), tuple("HEART", "❤️", 1L));
        assertThat(p101.myReaction()).isEqualTo("LIKE");

        // 반응 없는 post: 빈 목록, my_reaction null
        FeedItem p100 = itemOf(result, 100L);
        assertThat(p100.reactionCounts()).isEmpty();
        assertThat(p100.myReaction()).isNull();

        then(videoReactionReader).should(times(1)).getReactionCounts(anyList());
        then(videoReactionReader).should(times(1)).getMyReactions(anyList(), eq(1L));
    }

    @Test
    @DisplayName("SELECTING(최종선택 진행 중) 방은 촬영 불가라 모든 슬롯 current=false다")
    void givenSelecting_whenFeed_thenNoCurrentSlot() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        // opensAt 22:00 → now(22:10)엔 SELECTING. 창은 최종선택 오픈 이전 슬롯(20:00·21:00)만
        Room selecting = new Room(10L, RoomStatus.ACTIVE, DATE, 3, STARTS, null,
                STARTS, S22, DATE.atTime(22, 15), DATE.atTime(22, 30), DATE.atTime(23, 0));
        given(roomReader.findById(10L)).willReturn(Optional.of(selecting));
        given(roomMemberReader.getByRoomId(10L)).willReturn(List.of(member(20L, 1L), member(30L, 2L)));
        given(videoPostReader.getFeed(List.of(20L, 30L), List.of(S20, S21))).willReturn(List.of());

        FeedResponse result = service.getFeed(10L, 1L);

        assertThat(result.slots()).isEmpty();
        assertThat(result.slots()).noneMatch(SlotStatus::current);
    }

    @Test
    @DisplayName("아직 시작 전이라 노출 슬롯이 없으면 slots·posts 모두 빈 목록을 반환한다")
    void givenBeforeStart_whenFeed_thenEmpty() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(500L, 1L));
        // starts_at 미래(내일) → now(22:10)엔 노출 창 없음
        LocalDateTime starts = STARTS.plusDays(1);
        Room notStarted = new Room(10L, RoomStatus.PENDING, DATE, 3, null, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(2),
                starts.plusDays(2).plusHours(3), starts.plusDays(3));
        given(roomReader.findById(10L)).willReturn(Optional.of(notStarted));

        FeedResponse result = service.getFeed(10L, 1L);

        assertThat(result.slots()).isEmpty();
        assertThat(result.posts()).isEmpty();
        then(videoReactionReader).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenFeed_thenThrows() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service.getFeed(10L, 1L))
                .isInstanceOf(NotRoomMemberException.class);
    }

    // ── 과거 회고 페이지네이션(#350) ──────────────────────────────────────────

    /** 시작 시각만 다른 방(최종선택 오픈은 먼 미래로 고정). */
    private Room roomStartingAt(LocalDateTime startsAt) {
        return new Room(10L, RoomStatus.ACTIVE, DATE, 3, startsAt, null,
                startsAt, startsAt.plusDays(2), startsAt.plusDays(2).plusHours(2),
                startsAt.plusDays(2).plusHours(3), startsAt.plusDays(3));
    }

    @Test
    @DisplayName("현재 창이 비어도 historical cursor는 raw 슬롯 기준으로 진행된다")
    void givenEmptyHistoricalWindow_whenFeed_thenReturnsEmptyAndRawCursor() {
        // starts 18:00, now 22:10 → 창 [20,21,22], 그 이전(18·19시)이 남음 → next_cursor=20:00
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        given(roomReader.findById(10L)).willReturn(Optional.of(roomStartingAt(DATE.atTime(18, 0))));
        given(roomMemberReader.getByRoomId(10L)).willReturn(List.of(member(20L, 1L), member(30L, 2L)));
        given(videoPostReader.getFeed(List.of(20L, 30L), List.of(S20, S21, S22))).willReturn(List.of());

        FeedResponse result = service.getFeed(10L, 1L);

        assertThat(result.slots()).extracting(SlotStatus::slotStart).containsExactly(S22);
        assertThat(result.posts()).isEmpty();
        assertThat(result.nextCursor()).isEqualTo(S20);
    }

    @Test
    @DisplayName("공개 영상이 없는 historical page는 slots·posts가 비어도 raw cursor를 전진시킨다")
    void givenEmptyHistoricalPage_whenFeedBefore_thenReturnsEmptyAndAdvancesCursor() {
        LocalDateTime startsAt = DATE.atTime(10, 0);
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        given(roomReader.findById(10L)).willReturn(Optional.of(roomStartingAt(startsAt)));
        given(roomMemberReader.getByRoomId(10L)).willReturn(List.of(member(20L, 1L), member(30L, 2L)));
        given(videoPostReader.getFeed(List.of(20L, 30L), List.of(
                DATE.atTime(14, 0), DATE.atTime(15, 0), DATE.atTime(16, 0),
                DATE.atTime(17, 0), DATE.atTime(18, 0), DATE.atTime(19, 0)))).willReturn(List.of());

        FeedResponse result = service.getFeedBefore(10L, 1L, DATE.atTime(20, 0), 6);

        assertThat(result.slots()).isEmpty();
        assertThat(result.posts()).isEmpty();
        assertThat(result.nextCursor()).isEqualTo(DATE.atTime(14, 0));
    }

    @Test
    @DisplayName("before 커서 페이지는 공개 콘텐츠 슬롯만 반환하고 raw cursor로 이어진다")
    void givenBeforeCursor_whenFeedBefore_thenReturnsContentSlotsAndRawCursor() {
        LocalDateTime startsAt = DATE.atTime(10, 0);
        LocalDateTime s15 = DATE.atTime(15, 0);
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        given(roomReader.findById(10L)).willReturn(Optional.of(roomStartingAt(startsAt)));
        // before=20:00, size=6 → raw 슬롯 [14,15,16,17,18,19]
        given(roomMemberReader.getByRoomId(10L)).willReturn(List.of(member(20L, 1L), member(30L, 2L)));
        given(videoPostReader.getFeed(anyList(), anyList())).willReturn(List.of(
                post(200L, 30L, s15, "other-s15"),
                post(201L, 30L, DATE.atTime(16, 0), "other-s16")
        ));
        given(videoReactionReader.getReactionCounts(anyList())).willReturn(List.of());
        given(videoReactionReader.getMyReactions(anyList(), eq(1L))).willReturn(Map.of());

        FeedResponse result = service.getFeedBefore(10L, 1L, DATE.atTime(20, 0), 6);

        assertThat(result.slots()).extracting(SlotStatus::slotStart)
                .containsExactly(s15, DATE.atTime(16, 0));
        assertThat(result.slots()).allMatch(slot -> !slot.locked());
        assertThat(result.slots()).noneMatch(SlotStatus::current); // 과거엔 촬영 CTA 없음
        assertThat(result.posts()).extracting(FeedItem::videoPostId).containsExactly(200L, 201L);
        assertThat(result.nextCursor()).isEqualTo(DATE.atTime(14, 0)); // 아직 startsAt(10:00) 전
    }

    @Test
    @DisplayName("과거를 방 시작까지 넘기면 next_cursor가 null이다(더 과거 없음)")
    void givenReachRoomStart_whenFeedBefore_thenNextCursorNull() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        given(roomReader.findById(10L)).willReturn(Optional.of(roomStartingAt(DATE.atTime(18, 0))));
        given(roomMemberReader.getByRoomId(10L)).willReturn(List.of(member(20L, 1L), member(30L, 2L)));
        given(videoPostReader.getFeed(List.of(20L, 30L), List.of(DATE.atTime(18, 0), DATE.atTime(19, 0))))
                .willReturn(List.of());

        // before=20:00, startsAt=18:00 → [18,19]만, 가장 과거=18:00=startsAt → next_cursor null
        FeedResponse result = service.getFeedBefore(10L, 1L, DATE.atTime(20, 0), 6);

        assertThat(result.slots()).isEmpty();
        assertThat(result.posts()).isEmpty();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("size가 상한(24)을 넘겨도 raw feed window는 24개 오름차순 슬롯으로 제한된다")
    void givenOversizedRequest_whenFeedBefore_thenQueriesExactlyCappedRawWindow() {
        LocalDateTime startsAt = DATE.minusDays(2).atTime(0, 0);
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        given(roomReader.findById(10L)).willReturn(Optional.of(roomStartingAt(startsAt)));
        given(roomMemberReader.getByRoomId(10L)).willReturn(List.of(member(20L, 1L), member(30L, 2L)));
        LocalDateTime rawOldest = DATE.minusDays(1).atTime(0, 0);
        List<LocalDateTime> expectedRawWindow = IntStream.range(0, 24)
                .mapToObj(rawOldest::plusHours)
                .toList();
        given(videoPostReader.getFeed(eq(List.of(20L, 30L)), anyList())).willReturn(List.of());
        FeedResponse result = service.getFeedBefore(10L, 1L, DATE.atTime(0, 0), 100);

        assertThat(result.slots()).isEmpty();
        assertThat(result.nextCursor()).isEqualTo(rawOldest);
        then(videoPostReader).should().getFeed(List.of(20L, 30L), expectedRawWindow);
    }

    @Test
    @DisplayName("노출 창이 비었어도(최종선택 이후) 과거 이력이 있으면 회고 시작 커서를 시드한다")
    void givenWindowExpiredButHistory_whenFeed_thenSeedsArchiveCursor() {
        // starts 18:00, opensAt 20:00, now 22:10 → 노출 창은 전부 상한 이후라 빈다. 하지만 18·19시 이력 존재
        LocalDateTime opensAt = DATE.atTime(20, 0);
        Room room = new Room(10L, RoomStatus.ACTIVE, DATE, 3, DATE.atTime(18, 0), null,
                DATE.atTime(18, 0), opensAt, opensAt.plusHours(2), opensAt.plusHours(3), DATE.plusDays(1).atTime(0, 0));
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        given(roomReader.findById(10L)).willReturn(Optional.of(room));

        FeedResponse result = service.getFeed(10L, 1L);

        assertThat(result.slots()).isEmpty();
        assertThat(result.posts()).isEmpty();
        assertThat(result.nextCursor()).isEqualTo(opensAt); // 상한(20:00)을 커서로 → 클라가 before로 넘겨 과거 조회
    }

    @Test
    @DisplayName("구 모델 방(startsAt=null)은 과거 조회도 빈 응답이다")
    void givenLegacyRoom_whenFeedBefore_thenEmpty() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        Room legacy = new Room(10L, RoomStatus.ACTIVE, DATE, 3, STARTS, null); // startsAt null(구 모델)
        given(roomReader.findById(10L)).willReturn(Optional.of(legacy));

        FeedResponse result = service.getFeedBefore(10L, 1L, DATE.atTime(20, 0), 6);

        assertThat(result.slots()).isEmpty();
        assertThat(result.posts()).isEmpty();
        assertThat(result.nextCursor()).isNull();
    }
}
