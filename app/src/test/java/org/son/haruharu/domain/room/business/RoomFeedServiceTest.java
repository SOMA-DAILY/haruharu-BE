package org.son.haruharu.domain.room.business;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.dto.FeedResponse;
import org.son.haruharu.domain.room.business.dto.FeedResponse.FeedItem;
import org.son.haruharu.domain.room.business.dto.FeedResponse.SlotStatus;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.video.implement.ReactionCount;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.implement.VideoReactionReader;
import org.son.haruharu.infrastructure.s3.S3StoragePort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RoomFeedServiceTest {

    @Mock RoomMemberReader roomMemberReader;
    @Mock VideoPostReader videoPostReader;
    @Mock VideoReactionReader videoReactionReader;
    @Mock S3StoragePort s3StoragePort;

    RoomFeedService service;

    @BeforeEach
    void setUp() {
        service = new RoomFeedService(roomMemberReader, videoPostReader, videoReactionReader, s3StoragePort);
        // 비공개 영상 URL은 presigned GET URL로 서명해 응답한다(#162). 서명 적용 여부를 관찰하려고 접두사를 붙인다.
        lenient().when(s3StoragePort.createPresignedGetUrl(any()))
                .thenAnswer(inv -> inv.getArgument(0) == null ? null : "signed:" + inv.getArgument(0));
    }

    private RoomMember member(Long id, Long userId) {
        return new RoomMember(id, 999L, userId, "MALE", null, LocalDateTime.now());
    }

    private VideoPost post(Long postId, Long memberId, int slot, String url) {
        return new VideoPost(postId, memberId, 2, slot, null, null, url, null, false, null, null,
                LocalDateTime.now());
    }

    private boolean lockedOf(FeedResponse r, int slotIndex) {
        return r.slots().stream().filter(s -> s.slotIndex() == slotIndex)
                .map(SlotStatus::locked).findFirst().orElseThrow();
    }

    private FeedItem itemOf(FeedResponse r, Long postId) {
        return r.posts().stream().filter(p -> p.videoPostId().equals(postId)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("내가 day N에 한 슬롯도 안 올렸으면 전 슬롯이 잠기고 posts는 비며 피드 조회를 하지 않는다")
    void givenNoUpload_whenFeed_thenAllSlotsLocked() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        given(videoPostReader.getUploadedSlotIndexes(20L, 2)).willReturn(List.of());

        FeedResponse result = service.getFeed(10L, 1L, 2);

        assertThat(result.slots()).hasSize(5);
        assertThat(result.slots()).allMatch(SlotStatus::locked);
        assertThat(result.posts()).isEmpty();
        then(videoPostReader).should(never()).getFeed(anyList(), anyInt());
        then(videoReactionReader).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("업로드한 슬롯만 열리고, 각 post에 slot_index·반응 집계·내 반응이 배치 조회로 채워진다")
    void givenUploadedSomeSlots_whenFeed_thenSlotLockAndReactions() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        given(videoPostReader.getUploadedSlotIndexes(20L, 2)).willReturn(List.of(1, 2));
        given(roomMemberReader.getByRoomId(10L))
                .willReturn(List.of(member(20L, 1L), member(30L, 2L)));
        given(videoPostReader.getFeed(anyList(), anyInt())).willReturn(List.of(
                post(100L, 20L, 1, "my-s1"),
                post(101L, 30L, 1, "other-s1"),
                post(102L, 30L, 2, "other-s2"),
                post(103L, 30L, 3, "other-s3") // 잠긴 슬롯 → 필터링, 반응 조회 대상에서도 제외
        ));
        // 반응 집계: post 101에 LIKE 3, CURIOUS 1
        given(videoReactionReader.getReactionCounts(anyList())).willReturn(List.of(
                new ReactionCount(101L, "LIKE", 3),
                new ReactionCount(101L, "CURIOUS", 1)
        ));
        // 내 반응: post 101에 LIKE
        given(videoReactionReader.getMyReactions(anyList(), eq(1L))).willReturn(Map.of(101L, "LIKE"));

        FeedResponse result = service.getFeed(10L, 1L, 2);

        // 슬롯 잠금: 1·2 열림, 3·4·5 잠김
        assertThat(lockedOf(result, 1)).isFalse();
        assertThat(lockedOf(result, 2)).isFalse();
        assertThat(lockedOf(result, 3)).isTrue();
        assertThat(lockedOf(result, 4)).isTrue();
        assertThat(lockedOf(result, 5)).isTrue();
        // 잠긴 슬롯3 post 제외
        assertThat(result.posts()).extracting(FeedItem::videoPostId)
                .containsExactly(100L, 101L, 102L);
        assertThat(result.posts()).extracting(FeedItem::slotIndex).containsExactly(1, 1, 2);
        assertThat(result.posts()).extracting(FeedItem::mine).containsExactly(true, false, false);
        // 신고 연결용 작성자 user_id: roomMemberId 20→userId 1, 30→userId 2
        assertThat(result.posts()).extracting(FeedItem::userId).containsExactly(1L, 2L, 2L);
        // 비공개 원본이 아니라 서명된 재생 URL이 내려간다(#162)
        assertThat(itemOf(result, 101L).videoUrl()).isEqualTo("signed:other-s1");

        // post 101: 집계 + 내 반응
        FeedItem p101 = itemOf(result, 101L);
        assertThat(p101.reactionCounts()).extracting(
                FeedResponse.ReactionCounts::like,
                FeedResponse.ReactionCounts::curious,
                FeedResponse.ReactionCounts::empathy)
                .containsExactly(3L, 1L, 0L);
        assertThat(p101.myReaction()).isEqualTo("LIKE");

        // 반응 없는 post: 전부 0, my_reaction null
        FeedItem p100 = itemOf(result, 100L);
        assertThat(p100.reactionCounts()).extracting(
                FeedResponse.ReactionCounts::like,
                FeedResponse.ReactionCounts::curious,
                FeedResponse.ReactionCounts::empathy)
                .containsExactly(0L, 0L, 0L);
        assertThat(p100.myReaction()).isNull();

        // 배치: 반응 집계 1회 + 내 반응 1회 (post별 반복 없음)
        then(videoReactionReader).should(times(1)).getReactionCounts(anyList());
        then(videoReactionReader).should(times(1)).getMyReactions(anyList(), eq(1L));
    }

    @Test
    @DisplayName("슬롯이 없는 Day(0)면 slots·posts 모두 빈 목록을 반환한다")
    void givenDayWithoutSlots_whenFeed_thenEmpty() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(500L, 1L));

        FeedResponse result = service.getFeed(10L, 1L, 0);

        assertThat(result.slots()).isEmpty();
        assertThat(result.posts()).isEmpty();
        then(videoReactionReader).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenFeed_thenThrows() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service.getFeed(10L, 1L, 2))
                .isInstanceOf(NotRoomMemberException.class);
    }
}
