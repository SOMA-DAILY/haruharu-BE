package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.UUID;

/** 일상 영상 업로드. missionId가 있으면 미션 영상(열쇠 지급 대상). 업로드/삭제 로직은 #40. */
public record VideoPost(
        UUID id,
        UUID roomMemberId,
        UUID uploadSlotId,
        UUID missionId,
        String caption,
        String videoUrl,
        String thumbnailUrl,
        boolean isHidden,
        String hiddenReason,
        LocalDateTime deletedAt,
        LocalDateTime createdAt
) {}
