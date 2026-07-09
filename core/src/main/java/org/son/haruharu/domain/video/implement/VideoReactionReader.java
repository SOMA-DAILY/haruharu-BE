package org.son.haruharu.domain.video.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VideoReactionReader {

    private final VideoReactionRepository videoReactionRepository;

    public Optional<VideoReaction> findMine(Long videoPostId, Long userId) {
        return videoReactionRepository.findByVideoPostIdAndUserId(videoPostId, userId);
    }

    public List<ReactionCount> getReactionCounts(List<Long> videoPostIds) {
        return videoReactionRepository.countByVideoPostIds(videoPostIds);
    }

    /** videoPostId → 내 반응 타입. 반응 안 한 영상은 키가 없다. */
    public Map<Long, String> getMyReactions(List<Long> videoPostIds, Long userId) {
        return videoReactionRepository.findMineByVideoPostIds(videoPostIds, userId).stream()
                .collect(Collectors.toMap(VideoReaction::videoPostId, VideoReaction::reactionType));
    }
}
