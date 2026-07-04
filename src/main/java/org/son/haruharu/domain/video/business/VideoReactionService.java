package org.son.haruharu.domain.video.business;

import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.video.business.dto.ReactionResponse;
import org.son.haruharu.domain.video.business.exception.InvalidReactionTypeException;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.implement.VideoReaction;
import org.son.haruharu.domain.video.implement.VideoReactionReader;
import org.son.haruharu.domain.video.implement.VideoReactionWriter;
import org.son.haruharu.domain.video.storage.exception.VideoPostNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/** 영상 반응 토글. 유저당 영상당 1행(UNIQUE) — 없으면 생성/같은 타입이면 해제/다른 타입이면 교체. */
@Service
@Transactional
public class VideoReactionService {

    private static final Set<String> ALLOWED_TYPES = Set.of("LIKE", "CURIOUS", "EMPATHY");

    private final VideoPostReader videoPostReader;
    private final RoomMemberReader roomMemberReader;
    private final VideoReactionReader videoReactionReader;
    private final VideoReactionWriter videoReactionWriter;
    private final Clock clock;

    public VideoReactionService(VideoPostReader videoPostReader, RoomMemberReader roomMemberReader,
                                VideoReactionReader videoReactionReader,
                                VideoReactionWriter videoReactionWriter, Clock clock) {
        this.videoPostReader = videoPostReader;
        this.roomMemberReader = roomMemberReader;
        this.videoReactionReader = videoReactionReader;
        this.videoReactionWriter = videoReactionWriter;
        this.clock = clock;
    }

    public ReactionResponse toggle(Long userId, Long videoPostId, String reactionType) {
        if (!ALLOWED_TYPES.contains(reactionType)) {
            throw new InvalidReactionTypeException();
        }
        VideoPost post = videoPostReader.getById(videoPostId);
        if (post.deletedAt() != null) {
            throw new VideoPostNotFoundException();
        }
        // 인가 게이트: 반응하는 유저가 그 영상이 속한 방의 멤버여야 한다.
        RoomMember author = roomMemberReader.getById(post.roomMemberId());
        roomMemberReader.getByRoomIdAndUserId(author.roomId(), userId);

        Optional<VideoReaction> existing = videoReactionReader.findMine(videoPostId, userId);
        if (existing.isEmpty()) {
            videoReactionWriter.save(VideoReaction.create(videoPostId, userId, reactionType, LocalDateTime.now(clock)));
            return new ReactionResponse(true, reactionType);
        }
        VideoReaction current = existing.get();
        if (current.reactionType().equals(reactionType)) {
            videoReactionWriter.delete(current.id()); // 같은 타입 재탭 → 해제
            return new ReactionResponse(false, null);
        }
        videoReactionWriter.save(current.withType(reactionType)); // 다른 타입 → 교체
        return new ReactionResponse(true, reactionType);
    }
}
