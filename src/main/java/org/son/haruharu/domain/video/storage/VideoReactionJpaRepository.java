package org.son.haruharu.domain.video.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VideoReactionJpaRepository extends JpaRepository<VideoReactionEntity, Long> {

    Optional<VideoReactionEntity> findByVideoPostIdAndUserId(Long videoPostId, Long userId);

    @Query("select r.videoPostId as videoPostId, r.reactionType as reactionType, count(r) as count "
            + "from VideoReactionEntity r where r.videoPostId in :videoPostIds "
            + "group by r.videoPostId, r.reactionType")
    List<ReactionCountView> countByVideoPostIds(@Param("videoPostIds") List<Long> videoPostIds);

    List<VideoReactionEntity> findByVideoPostIdInAndUserId(List<Long> videoPostIds, Long userId);
}
