package org.son.haruharu.domain.video.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.video.business.dto.ReactionResponse;
import org.son.haruharu.domain.video.business.event.VideoReactedEvent;
import org.son.haruharu.domain.video.business.exception.CannotReactToOwnVideoException;
import org.son.haruharu.domain.video.implement.ReactionType;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.implement.VideoReaction;
import org.son.haruharu.domain.video.implement.VideoReactionReader;
import org.son.haruharu.domain.video.implement.VideoReactionWriter;
import org.springframework.context.ApplicationEventPublisher;

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
    @Mock ProfileService profileService;
    @Mock ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private VideoReactionService service() {
        return new VideoReactionService(videoPostReader, roomMemberReader, videoReactionReader,
                videoReactionWriter, profileService, eventPublisher, clock);
    }

    private void stubPostAndMembership(Long postId, Long userId) {
        Long authorMemberId = 40L;
        Long roomId = 50L;
        VideoPost post = new VideoPost(postId, authorMemberId, 1, 1, null, null,
                "url", null, false, null, null, LocalDateTime.now());
        given(videoPostReader.getById(postId)).willReturn(post);
        given(roomMemberReader.getById(authorMemberId))
                .willReturn(new RoomMember(authorMemberId, roomId, 2L, "FEMALE", null, LocalDateTime.now()));
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
                .willReturn(new RoomMember(700L, roomId, userId, "MALE", null, LocalDateTime.now()));
    }

    @Test
    @DisplayName("반응이 없으면 새로 생성하고 reacted=true를 반환한다")
    void givenNoReaction_whenToggle_thenInsert() {
        Long userId = 1L;
        Long postId = 1L;
        stubPostAndMembership(postId, userId);
        given(videoReactionReader.findMine(postId, userId)).willReturn(Optional.empty());

        ReactionResponse result = service().toggle(userId, postId, ReactionType.LIKE);

        assertThat(result.reacted()).isTrue();
        assertThat(result.reactionType()).isEqualTo(ReactionType.LIKE);
        then(videoReactionWriter).should().save(any());
        then(videoReactionWriter).should(never()).delete(any());
    }

    @Test
    @DisplayName("같은 타입 재탭 시 반응이 해제(삭제)되고 reacted=false를 반환한다")
    void givenSameType_whenToggle_thenRemove() {
        Long userId = 1L;
        Long postId = 1L;
        Long reactionId = 2L;
        stubPostAndMembership(postId, userId);
        given(videoReactionReader.findMine(postId, userId))
                .willReturn(Optional.of(new VideoReaction(reactionId, postId, userId, "LIKE", LocalDateTime.now())));

        ReactionResponse result = service().toggle(userId, postId, ReactionType.LIKE);

        assertThat(result.reacted()).isFalse();
        then(videoReactionWriter).should().delete(reactionId);
        then(videoReactionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("다른 타입으로 토글 시 같은 행을 교체하고 reacted=true·새 타입을 반환한다")
    void givenDifferentType_whenToggle_thenReplace() {
        Long userId = 1L;
        Long postId = 1L;
        Long reactionId = 2L;
        stubPostAndMembership(postId, userId);
        given(videoReactionReader.findMine(postId, userId))
                .willReturn(Optional.of(new VideoReaction(reactionId, postId, userId, "LIKE", LocalDateTime.now())));

        ReactionResponse result = service().toggle(userId, postId, ReactionType.HEART);

        assertThat(result.reacted()).isTrue();
        assertThat(result.reactionType()).isEqualTo(ReactionType.HEART);
        then(videoReactionWriter).should().save(any());
        then(videoReactionWriter).should(never()).delete(any());
    }

    @Test
    @DisplayName("다른 유저가 새 반응을 남기면 반응자 닉네임을 담은 VideoReactedEvent가 작성자에게 발행된다")
    void givenNewReactionByOther_whenToggle_thenPublishesEventWithReactorNickname() {
        Long userId = 1L; // 작성자(2L)와 다른 유저
        Long postId = 100L;
        stubPostAndMembership(postId, userId);
        given(videoReactionReader.findMine(postId, userId)).willReturn(Optional.empty());
        given(profileService.getNickname(userId)).willReturn("노을");

        service().toggle(userId, postId, ReactionType.LIKE);

        then(eventPublisher).should().publishEvent(new VideoReactedEvent(2L, postId, "노을", "LIKE"));
    }

    @Test
    @DisplayName("자기 자신의 영상에 반응하면 CannotReactToOwnVideoException이 발생하고 저장·이벤트가 없다")
    void givenOwnVideo_whenToggle_thenThrowsCannotReactToOwnVideo() {
        Long userId = 2L; // 작성자 본인
        Long postId = 100L;
        Long authorMemberId = 40L;
        Long roomId = 50L;
        VideoPost post = new VideoPost(postId, authorMemberId, 1, 1, null, null,
                "url", null, false, null, null, LocalDateTime.now());
        given(videoPostReader.getById(postId)).willReturn(post);
        given(roomMemberReader.getById(authorMemberId))
                .willReturn(new RoomMember(authorMemberId, roomId, userId, "FEMALE", null, LocalDateTime.now()));

        assertThatThrownBy(() -> service().toggle(userId, postId, ReactionType.LIKE))
                .isInstanceOf(CannotReactToOwnVideoException.class);
        then(videoReactionWriter).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("반응 해제(같은 타입 재탭) 시 이벤트를 발행하지 않는다")
    void givenSameType_whenToggle_thenNoEvent() {
        Long userId = 1L;
        Long postId = 100L;
        stubPostAndMembership(postId, userId);
        given(videoReactionReader.findMine(postId, userId))
                .willReturn(Optional.of(new VideoReaction(3L, postId, userId, "LIKE", LocalDateTime.now())));

        service().toggle(userId, postId, ReactionType.LIKE);

        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("영상이 속한 방의 멤버가 아니면 NotRoomMemberException")
    void givenNonMember_whenToggle_thenThrows() {
        Long userId = 1L;
        Long postId = 1L;
        Long authorMemberId = 41L;
        Long roomId = 51L;
        VideoPost post = new VideoPost(postId, authorMemberId, 1, 1, null, null,
                "url", null, false, null, null, LocalDateTime.now());
        given(videoPostReader.getById(postId)).willReturn(post);
        given(roomMemberReader.getById(authorMemberId))
                .willReturn(new RoomMember(authorMemberId, roomId, 2L, "FEMALE", null, LocalDateTime.now()));
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service().toggle(userId, postId, ReactionType.LIKE))
            .isInstanceOf(NotRoomMemberException.class);
    }
}
