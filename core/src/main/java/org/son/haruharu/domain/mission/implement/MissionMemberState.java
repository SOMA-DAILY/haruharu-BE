package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;

/** 오픈 시점 대상 멤버의 노출·활동·개인 카드 해소 상태. 영상/채팅 원문은 보유하지 않는다. */
public record MissionMemberState(
        Long id,
        Long roomMissionId,
        Long roomMemberId,
        ActivationChannel expectedChannel,
        LocalDateTime firstImpressedAt,
        LocalDateTime lastImpressedAt,
        int expectedActivityCount,
        LocalDateTime firstExpectedActivityAt,
        LocalDateTime lastExpectedActivityAt,
        int crossChannelActivityCount,
        LocalDateTime firstCrossChannelActivityAt,
        LocalDateTime lastCrossChannelActivityAt,
        MissionCardResolution resolution,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
