package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 캡션 수정 이력(#558). 수정할 때 바뀌기 전 값을 남기고, 과거 시각의 캡션을 복원한다.
 * 이력은 이 기능 배포 이후의 수정만 있다. 그 전에 바뀐 캡션은 현재 값과 구분할 수 없다.
 */
@Component
@RequiredArgsConstructor
public class VideoCaptionHistory {

    private final VideoCaptionRevisionRepository repository;

    public void record(Long videoPostId, String previousCaption, String newCaption, LocalDateTime changedAt) {
        if (Objects.equals(previousCaption, newCaption)) {
            return;
        }
        repository.save(VideoCaptionRevision.of(videoPostId, previousCaption, newCaption, changedAt));
    }

    /**
     * 영상별 {@code at} 시점의 캡션. {@code at} 이후 수정이 있으면 가장 이른 수정의 이전 값이 그때의 캡션이다.
     * 이후 수정이 없는 영상은 결과에 없다(현재 캡션이 그때의 캡션이다).
     */
    public Map<Long, String> captionsAt(Collection<Long> videoPostIds, LocalDateTime at) {
        if (videoPostIds.isEmpty()) {
            return Map.of();
        }
        List<VideoCaptionRevision> changes = repository.findChangedAfter(videoPostIds, at);
        Map<Long, String> result = new HashMap<>();
        for (VideoCaptionRevision change : changes) {
            result.putIfAbsent(change.videoPostId(), change.previousCaption() == null ? "" : change.previousCaption());
        }
        return result;
    }
}
