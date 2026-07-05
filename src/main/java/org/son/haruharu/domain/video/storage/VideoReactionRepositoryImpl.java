package org.son.haruharu.domain.video.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.implement.ReactionCount;
import org.son.haruharu.domain.video.implement.VideoReaction;
import org.son.haruharu.domain.video.implement.VideoReactionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class VideoReactionRepositoryImpl implements VideoReactionRepository {

    private final VideoReactionJpaRepository jpaRepository;

    @Override
    public Optional<VideoReaction> findByVideoPostIdAndUserId(Long videoPostId, Long userId) {
        return jpaRepository.findByVideoPostIdAndUserId(videoPostId, userId).map(VideoReactionEntity::toDomain);
    }

    @Override
    public List<ReactionCount> countByVideoPostIds(List<Long> videoPostIds) {
        if (videoPostIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.countByVideoPostIds(videoPostIds).stream()
                .map(v -> new ReactionCount(v.getVideoPostId(), v.getReactionType(), v.getCount()))
                .toList();
    }

    @Override
    public List<VideoReaction> findMineByVideoPostIds(List<Long> videoPostIds, Long userId) {
        if (videoPostIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByVideoPostIdInAndUserId(videoPostIds, userId).stream()
                .map(VideoReactionEntity::toDomain)
                .toList();
    }

    @Override
    public VideoReaction save(VideoReaction reaction) {
        return jpaRepository.save(VideoReactionEntity.from(reaction)).toDomain();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}
