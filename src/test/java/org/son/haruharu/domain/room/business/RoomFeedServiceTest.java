package org.son.haruharu.domain.room.business;

import java.time.LocalDateTime;
import java.util.List;

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
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RoomFeedServiceTest {

    @Mock RoomMemberReader roomMemberReader;
    @Mock VideoPostReader videoPostReader;

    RoomFeedService service;

    @BeforeEach
    void setUp() {
        service = new RoomFeedService(roomMemberReader, videoPostReader);
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
    }

    @Test
    @DisplayName("내가 업로드한 슬롯만 잠금이 풀리고, 그 슬롯 영상만 slot_index와 함께 노출된다")
    void givenUploadedSomeSlots_whenFeed_thenOnlyThoseSlotsUnlocked() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(20L, 1L));
        given(videoPostReader.getUploadedSlotIndexes(20L, 2)).willReturn(List.of(1, 2));
        given(roomMemberReader.getByRoomId(10L))
                .willReturn(List.of(member(20L, 1L), member(30L, 2L)));
        given(videoPostReader.getFeed(anyList(), anyInt())).willReturn(List.of(
                post(100L, 20L, 1, "my-s1"),
                post(101L, 30L, 1, "other-s1"),
                post(102L, 30L, 2, "other-s2"),
                post(103L, 30L, 3, "other-s3") // 잠긴 슬롯 → 필터링
        ));

        FeedResponse result = service.getFeed(10L, 1L, 2);

        // 슬롯 잠금: 1·2 열림, 3·4·5 잠김
        assertThat(lockedOf(result, 1)).isFalse();
        assertThat(lockedOf(result, 2)).isFalse();
        assertThat(lockedOf(result, 3)).isTrue();
        assertThat(lockedOf(result, 4)).isTrue();
        assertThat(lockedOf(result, 5)).isTrue();
        // posts: 슬롯 3 제외, slot_index 노출, mine 플래그
        assertThat(result.posts()).extracting(FeedItem::slotIndex).containsExactly(1, 1, 2);
        assertThat(result.posts()).extracting(FeedItem::mine).containsExactly(true, false, false);
    }

    @Test
    @DisplayName("슬롯이 없는 Day(0)면 slots·posts 모두 빈 목록을 반환한다")
    void givenDayWithoutSlots_whenFeed_thenEmpty() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(500L, 1L));

        FeedResponse result = service.getFeed(10L, 1L, 0);

        assertThat(result.slots()).isEmpty();
        assertThat(result.posts()).isEmpty();
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenFeed_thenThrows() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service.getFeed(10L, 1L, 2))
                .isInstanceOf(NotRoomMemberException.class);
    }
}
