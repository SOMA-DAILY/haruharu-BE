package org.son.haruharu.domain.video.business.event;

import java.time.LocalDateTime;

/** 실제 영상 저장 성공 신호. 영상 URL·caption 등 원문은 포함하지 않는다. */
public record VideoUploadedEvent(
        Long videoPostId,
        Long roomId,
        Long roomMemberId,
        LocalDateTime occurredAt
) {}
