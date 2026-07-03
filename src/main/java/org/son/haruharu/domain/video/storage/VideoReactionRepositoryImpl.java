package org.son.haruharu.domain.video.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.implement.VideoReaction;
import org.son.haruharu.domain.video.implement.VideoReactionRepository;
import org.springframework.stereotype.Repository;

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
    public VideoReaction save(VideoReaction reaction) {
        return jpaRepository.save(VideoReactionEntity.from(reaction)).toDomain();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}
