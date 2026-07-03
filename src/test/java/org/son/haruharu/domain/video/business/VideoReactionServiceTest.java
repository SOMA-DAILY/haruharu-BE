package org.son.haruharu.domain.video.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.video.business.dto.ReactionResponse;
import org.son.haruharu.domain.video.business.exception.InvalidReactionTypeException;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.implement.VideoReaction;
import org.son.haruharu.domain.video.implement.VideoReactionReader;
import org.son.haruharu.domain.video.implement.VideoReactionWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class VideoReactionServiceTest {

    @Mock VideoPostReader videoPostReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock VideoReactionReader videoReactionReader;
    @Mock VideoReactionWriter videoReactionWriter;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private VideoReactionService service() {
        return new VideoReactionService(videoPostReader, roomMemberReader, videoReactionReader, videoReactionWriter, clock);
    }

    private void stubPostAndMembership(UUID postId, UUID userId) {
        UUID authorMemberId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        VideoPost post = new VideoPost(postId, authorMemberId, 1, 1, null, null,
                "url", null, false, null, null, LocalDateTime.now());
        given(videoPostReader.getById(postId)).willReturn(post);
        given(roomMemberReader.getById(authorMemberId))
                .willReturn(new RoomMember(authorMemberId, roomId, UUID.randomUUID(), "FEMALE", null, LocalDateTime.now()));
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
                .willReturn(new RoomMember(UUID.randomUUID(), roomId, userId, "MALE", null, LocalDateTime.now()));
    }

    @Test
    @DisplayName("반응이 없으면 새로 생성하고 reacted=true를 반환한다")
    void givenNoReaction_whenToggle_thenInsert() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        stubPostAndMembership(postId, userId);
        given(videoReactionReader.findMine(postId, userId)).willReturn(Optional.empty());

        ReactionResponse result = service().toggle(userId, postId, "LIKE");

        assertThat(result.reacted()).isTrue();
        assertThat(result.reactionType()).isEqualTo("LIKE");
        then(videoReactionWriter).should().save(any());
        then(videoReactionWriter).should(never()).delete(any());
    }

    @Test
    @DisplayName("같은 타입 재탭 시 반응이 해제(삭제)되고 reacted=false를 반환한다")
    void givenSameType_whenToggle_thenRemove() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID reactionId = UUID.randomUUID();
        stubPostAndMembership(postId, userId);
        given(videoReactionReader.findMine(postId, userId))
                .willReturn(Optional.of(new VideoReaction(reactionId, postId, userId, "LIKE", LocalDateTime.now())));

        ReactionResponse result = service().toggle(userId, postId, "LIKE");

        assertThat(result.reacted()).isFalse();
        then(videoReactionWriter).should().delete(reactionId);
        then(videoReactionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("다른 타입으로 토글 시 같은 행을 교체하고 reacted=true·새 타입을 반환한다")
    void givenDifferentType_whenToggle_thenReplace() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID reactionId = UUID.randomUUID();
        stubPostAndMembership(postId, userId);
        given(videoReactionReader.findMine(postId, userId))
                .willReturn(Optional.of(new VideoReaction(reactionId, postId, userId, "LIKE", LocalDateTime.now())));

        ReactionResponse result = service().toggle(userId, postId, "CURIOUS");

        assertThat(result.reacted()).isTrue();
        assertThat(result.reactionType()).isEqualTo("CURIOUS");
        then(videoReactionWriter).should().save(any());
        then(videoReactionWriter).should(never()).delete(any());
    }

    @Test
    @DisplayName("허용하지 않는 반응 타입이면 InvalidReactionTypeException")
    void givenInvalidType_whenToggle_thenThrows() {
        assertThatThrownBy(() -> service().toggle(UUID.randomUUID(), UUID.randomUUID(), "ANGRY"))
            .isInstanceOf(InvalidReactionTypeException.class);
    }

    @Test
    @DisplayName("영상이 속한 방의 멤버가 아니면 NotRoomMemberException")
    void givenNonMember_whenToggle_thenThrows() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID authorMemberId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        VideoPost post = new VideoPost(postId, authorMemberId, 1, 1, null, null,
                "url", null, false, null, null, LocalDateTime.now());
        given(videoPostReader.getById(postId)).willReturn(post);
        given(roomMemberReader.getById(authorMemberId))
                .willReturn(new RoomMember(authorMemberId, roomId, UUID.randomUUID(), "FEMALE", null, LocalDateTime.now()));
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service().toggle(userId, postId, "LIKE"))
            .isInstanceOf(NotRoomMemberException.class);
    }
}
