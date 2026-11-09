package org.son.haruharu.domain.video.storage;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.implement.VideoCaptionRevision;
import org.son.haruharu.domain.video.implement.VideoCaptionRevisionRepository;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class VideoCaptionRevisionRepositoryImpl implements VideoCaptionRevisionRepository {

    private final VideoCaptionRevisionJpaRepository jpaRepository;

    @Override
    public VideoCaptionRevision save(VideoCaptionRevision revision) {
        return jpaRepository.save(VideoCaptionRevisionEntity.from(revision)).toDomain();
    }

    @Override
    public List<VideoCaptionRevision> findChangedAfter(Collection<Long> videoPostIds, LocalDateTime after) {
        if (videoPostIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository
                .findByVideoPostIdInAndChangedAtAfterOrderByVideoPostIdAscChangedAtAscIdAsc(videoPostIds, after)
                .stream()
                .map(VideoCaptionRevisionEntity::toDomain)
                .toList();
    }
}
