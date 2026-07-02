package org.son.haruharu.domain.room.business;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.dto.FeedResponse;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RoomFeedServiceTest {

    @Mock RoomMemberReader roomMemberReader;
    @Mock VideoPostReader videoPostReader;

    RoomFeedService service;

    @BeforeEach
    void setUp() {
        service = new RoomFeedService(roomMemberReader, videoPostReader);
    }

    private RoomMember me(UUID id, UUID userId) {
        return new RoomMember(id, UUID.randomUUID(), userId, "MALE", null, LocalDateTime.now());
    }

    private VideoPost post(UUID memberId, String url) {
        return new VideoPost(UUID.randomUUID(), memberId, 1, 1, null, null, url, null, false, null, null,
                LocalDateTime.now());
    }

    @Test
    @DisplayName("내가 day N에 한 건도 안 올렸으면 피드가 잠긴다(locked, 빈 목록)")
    void givenNoUpload_whenFeed_thenLocked() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me(memberId, userId));
        given(videoPostReader.countUploaded(any(), anyInt())).willReturn(0L);

        FeedResponse result = service.getFeed(roomId, userId, 1);

        assertThat(result.locked()).isTrue();
        assertThat(result.posts()).isEmpty();
    }

    @Test
    @DisplayName("내가 day N에 업로드했으면 잠금이 풀리고 멤버 영상이 보이며 내 영상은 mine=true")
    void givenUploaded_whenFeed_thenUnlockedWithPosts() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID otherMemberId = UUID.randomUUID();
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me(memberId, userId));
        given(videoPostReader.countUploaded(any(), anyInt())).willReturn(1L);
        given(roomMemberReader.getByRoomId(roomId))
                .willReturn(List.of(me(memberId, userId), me(otherMemberId, UUID.randomUUID())));
        given(videoPostReader.getFeed(anyList(), eq(1))).willReturn(List.of(
                post(memberId, "myUrl"),
                post(otherMemberId, "otherUrl")
        ));

        FeedResponse result = service.getFeed(roomId, userId, 1);

        assertThat(result.locked()).isFalse();
        assertThat(result.posts()).hasSize(2);
        assertThat(result.posts()).extracting(FeedResponse.FeedItem::mine).containsExactly(true, false);
        // 피드는 이 방 멤버 id로만 조회(다른 방 영상 유입 차단)
        then(videoPostReader).should().getFeed(List.of(memberId, otherMemberId), 1);
    }

    @Test
    @DisplayName("슬롯이 없는 Day(0)면 잠금 없이 빈 목록을 반환한다")
    void givenDayWithoutSlots_whenFeed_thenEmptyUnlocked() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me(UUID.randomUUID(), userId));

        FeedResponse result = service.getFeed(roomId, userId, 0);

        assertThat(result.locked()).isFalse();
        assertThat(result.posts()).isEmpty();
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenFeed_thenThrows() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service.getFeed(roomId, userId, 1))
            .isInstanceOf(NotRoomMemberException.class);
    }
}
