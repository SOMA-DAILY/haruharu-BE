package org.son.haruharu.domain.video.storage;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoCaptionRevisionJpaRepository extends JpaRepository<VideoCaptionRevisionEntity, Long> {

    List<VideoCaptionRevisionEntity> findByVideoPostIdInAndChangedAtAfterOrderByVideoPostIdAscChangedAtAscIdAsc(
            Collection<Long> videoPostIds, LocalDateTime after);
}
