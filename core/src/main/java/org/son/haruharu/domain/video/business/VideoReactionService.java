package org.son.haruharu.domain.video.business;

import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.InteractionGuidanceWriter;
import org.son.haruharu.domain.video.business.dto.ReactionResponse;
import org.son.haruharu.domain.video.business.exception.CannotReactToOwnVideoException;
import org.son.haruharu.domain.video.implement.ReactionType;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.implement.VideoReaction;
import org.son.haruharu.domain.video.implement.VideoReactionReader;
import org.son.haruharu.domain.video.implement.VideoReactionWriter;
import org.son.haruharu.domain.video.business.event.VideoReactedEvent;
import org.son.haruharu.domain.video.storage.exception.VideoPostNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/** 영상 반응 토글. 유저당 영상당 1행(UNIQUE) — 없으면 생성/같은 타입이면 해제/다른 타입이면 교체. */
@Service
@Transactional
public class VideoReactionService {

    private final VideoPostReader videoPostReader;
    private final RoomMemberReader roomMemberReader;
    private final VideoReactionReader videoReactionReader;
    private final VideoReactionWriter videoReactionWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final InteractionGuidanceWriter guidanceWriter;

    public VideoReactionService(VideoPostReader videoPostReader, RoomMemberReader roomMemberReader,
                                VideoReactionReader videoReactionReader,
                                VideoReactionWriter videoReactionWriter,
                                ApplicationEventPublisher eventPublisher, Clock clock,
                                InteractionGuidanceWriter guidanceWriter) {
        this.videoPostReader = videoPostReader;
        this.roomMemberReader = roomMemberReader;
        this.videoReactionReader = videoReactionReader;
        this.videoReactionWriter = videoReactionWriter;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.guidanceWriter = guidanceWriter;
    }

    public ReactionResponse toggle(Long userId, Long videoPostId, ReactionType reactionType) {
        VideoPost post = videoPostReader.getById(videoPostId);
        if (post.deletedAt() != null) {
            throw new VideoPostNotFoundException();
        }
        // 자기 자신의 영상에는 반응할 수 없다(설계 4-5: 타인 영상만 반응). 클라 mine 플래그에 의존하지 않고 서버가 차단.
        RoomMember author = roomMemberReader.getById(post.roomMemberId());
        if (author.userId().equals(userId)) {
            throw new CannotReactToOwnVideoException();
        }
        // 인가 게이트: 반응하는 유저가 그 영상이 속한 방의 멤버여야 한다. 반환값은 알림 표시 이름(가명)에 재사용한다.
        RoomMember reactor = roomMemberReader.getByRoomIdAndUserId(author.roomId(), userId);

        Optional<VideoReaction> existing = videoReactionReader.findMine(videoPostId, userId);
        if (existing.isEmpty()) {
            videoReactionWriter.save(VideoReaction.create(videoPostId, userId, reactionType.name(), LocalDateTime.now(clock)));
            guidanceWriter.recordFirstReaction(reactor);
            if (!author.userId().equals(userId)) {
                // 반응 알림도 매칭 전에는 실명이 아니라 방 세션 가명(없으면 익명)으로 표기한다(#393 — 그룹채팅과 동일 정책).
                eventPublisher.publishEvent(new VideoReactedEvent(
                        author.userId(), userId, videoPostId, reactor.displayPseudonym(), reactionType.name()));
            }
            return new ReactionResponse(true, reactionType);
        }
        VideoReaction current = existing.get();
        if (current.reactionType().equals(reactionType.name())) {
            videoReactionWriter.delete(current.id()); // 같은 타입 재탭 → 해제
            return new ReactionResponse(false, null);
        }
        videoReactionWriter.save(current.withType(reactionType.name())); // 다른 타입 → 교체
        guidanceWriter.recordFirstReaction(reactor);
        return new ReactionResponse(true, reactionType);
    }
}
