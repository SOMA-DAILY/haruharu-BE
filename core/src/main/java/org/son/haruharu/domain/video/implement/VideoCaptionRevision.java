package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;

/** 캡션 수정 한 번. 바뀌기 전 값을 남겨 과거 어느 시각의 캡션이든 복원할 수 있게 한다(#558). */
public record VideoCaptionRevision(
        Long id,
        Long videoPostId,
        String previousCaption,
        String newCaption,
        LocalDateTime changedAt
) {
    public static VideoCaptionRevision of(Long videoPostId, String previousCaption, String newCaption,
                                          LocalDateTime changedAt) {
        return new VideoCaptionRevision(null, videoPostId, previousCaption, newCaption, changedAt);
    }
}
