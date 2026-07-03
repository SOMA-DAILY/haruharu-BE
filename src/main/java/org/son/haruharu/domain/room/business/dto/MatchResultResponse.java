package org.son.haruharu.domain.room.business.dto;

import java.time.LocalDateTime;

/** 내 매칭 결과. 매칭 상대의 방 멤버 id와 성사 시각. */
public record MatchResultResponse(
        Long partnerMemberId,
        LocalDateTime matchedAt
) {}
