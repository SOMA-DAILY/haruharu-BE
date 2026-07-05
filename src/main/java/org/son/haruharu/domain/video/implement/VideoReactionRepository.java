package org.son.haruharu.domain.video.implement;

import java.util.List;
import java.util.Optional;

public interface VideoReactionRepository {

    Optional<VideoReaction> findByVideoPostIdAndUserId(Long videoPostId, Long userId);

    /** 영상들의 타입별 반응 수 집계(배치). 반응이 없는 (post,type)은 행이 없다. */
    List<ReactionCount> countByVideoPostIds(List<Long> videoPostIds);

    /** 영상들에 대한 내 반응(배치). 유저당 영상당 최대 1행. */
    List<VideoReaction> findMineByVideoPostIds(List<Long> videoPostIds, Long userId);

    VideoReaction save(VideoReaction reaction);

    void deleteById(Long id);
}
