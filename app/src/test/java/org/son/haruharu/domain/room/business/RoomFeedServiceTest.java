package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import static org.mockito.Mockito.never;
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
        return new VideoPost(postId, memberId, 1, slotStart, null, null, url, null, false, null, null,
                LocalDateTime.now());
    }

    private boolean lockedOf(FeedResponse r, LocalDateTime slotStart) {
        return r.slots().stream().filter(s -> s.slotStart().equals(slotStart))
                .map(SlotStatus::locked).findFirst().orElseThrow();
    }

    private FeedItem itemOf(FeedResponse r, Long postId) {
        return r.posts().stream().filter(p -> p.videoPostId().equals(postId)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("한 슬롯도 안 올렸으면 노출 창 전 슬롯이 잠기고 posts는 비며 피드 조회를 하지 않는다")
    void givenNoUpload_whenFeed_thenAllSlotsLocked() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        given(roomReader.findById(10L)).willReturn(Optional.of(activeRoom()));
        given(videoPostReader.getUploadedSlotStarts(eq(20L), anyList())).willReturn(List.of());

        FeedResponse result = service.getFeed(10L, 1L);

        assertThat(result.slots()).extracting(SlotStatus::slotStart).containsExactly(S20, S21, S22);
        assertThat(result.slots()).allMatch(SlotStatus::locked);
        assertThat(result.posts()).isEmpty();
        then(videoPostReader).should(never()).getFeed(anyList(), anyList());
        then(videoReactionReader).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("업로드한 슬롯만 열리고, 각 post에 slot_start·반응 집계·내 반응이 배치 조회로 채워진다")
    void givenUploadedSomeSlots_whenFeed_thenSlotLockAndReactions() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        given(roomReader.findById(10L)).willReturn(Optional.of(activeRoom()));
        given(videoPostReader.getUploadedSlotStarts(eq(20L), anyList())).willReturn(List.of(S21, S22));
        given(roomMemberReader.getByRoomId(10L))
                .willReturn(List.of(member(20L, 1L), member(30L, 2L)));
        given(videoPostReader.getFeed(anyList(), anyList())).willReturn(List.of(
                post(100L, 20L, S21, "my-s21"),
                post(101L, 30L, S21, "other-s21"),
                post(102L, 30L, S22, "other-s22"),
                post(103L, 30L, S20, "other-s20") // 잠긴 슬롯(20:00) → 필터링, 반응 조회 대상에서도 제외
        ));
        given(videoReactionReader.getReactionCounts(anyList())).willReturn(List.of(
                new ReactionCount(101L, "LIKE", 3),
                new ReactionCount(101L, "HEART", 1)
        ));
        given(videoReactionReader.getMyReactions(anyList(), eq(1L))).willReturn(Map.of(101L, "LIKE"));

        FeedResponse result = service.getFeed(10L, 1L);

        // 슬롯 잠금: 20:00 잠김, 21:00·22:00 열림
        assertThat(lockedOf(result, S20)).isTrue();
        assertThat(lockedOf(result, S21)).isFalse();
        assertThat(lockedOf(result, S22)).isFalse();
        // 잠긴 슬롯(20:00) post 제외
        assertThat(result.posts()).extracting(FeedItem::videoPostId).containsExactly(100L, 101L, 102L);
        assertThat(result.posts()).extracting(FeedItem::slotStart).containsExactly(S21, S21, S22);
        assertThat(result.posts()).extracting(FeedItem::mine).containsExactly(true, false, false);
        assertThat(result.posts()).extracting(FeedItem::userId).containsExactly(1L, 2L, 2L);
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
}
