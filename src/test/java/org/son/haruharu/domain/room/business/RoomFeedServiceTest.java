package org.son.haruharu.domain.room.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.mission.implement.UploadSlotReader;
import org.son.haruharu.domain.room.business.dto.FeedResponse;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RoomFeedServiceTest {

    @Mock RoomMemberReader roomMemberReader;
    @Mock UploadSlotReader uploadSlotReader;
    @Mock VideoPostReader videoPostReader;

    RoomFeedService service;

    @BeforeEach
    void setUp() {
        service = new RoomFeedService(roomMemberReader, uploadSlotReader, videoPostReader);
    }

    private RoomMember me(UUID id, UUID userId) {
        return new RoomMember(id, UUID.randomUUID(), userId, "MALE", 0, LocalDateTime.now());
    }

    @Test
    @DisplayName("내가 day N에 한 건도 안 올렸으면 피드가 잠긴다(locked, 빈 목록)")
    void givenNoUpload_whenFeed_thenLocked() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me(memberId, userId));
        given(uploadSlotReader.getByDay(1)).willReturn(List.of(new UploadSlot(UUID.randomUUID(), 1, 1, 8, 11)));
        given(videoPostReader.countUploaded(any(), anyList())).willReturn(0L);

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
        UUID slotId = UUID.randomUUID();
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me(memberId, userId));
        given(uploadSlotReader.getByDay(1)).willReturn(List.of(new UploadSlot(slotId, 1, 1, 8, 11)));
        given(videoPostReader.countUploaded(any(), anyList())).willReturn(1L);
        given(videoPostReader.getFeed(anyList())).willReturn(List.of(
                new VideoPost(UUID.randomUUID(), memberId, slotId, null, null, "myUrl", null, false, null, null, LocalDateTime.now()),
                new VideoPost(UUID.randomUUID(), otherMemberId, slotId, null, null, "otherUrl", null, false, null, null, LocalDateTime.now())
        ));

        FeedResponse result = service.getFeed(roomId, userId, 1);

        assertThat(result.locked()).isFalse();
        assertThat(result.posts()).hasSize(2);
        assertThat(result.posts()).extracting(FeedResponse.FeedItem::mine).containsExactly(true, false);
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
