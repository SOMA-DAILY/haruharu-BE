package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface VideoCaptionRevisionRepository {

    VideoCaptionRevision save(VideoCaptionRevision revision);

    /** 주어진 시각 이후의 수정(영상별 시간순). */
    List<VideoCaptionRevision> findChangedAfter(Collection<Long> videoPostIds, LocalDateTime after);
}
