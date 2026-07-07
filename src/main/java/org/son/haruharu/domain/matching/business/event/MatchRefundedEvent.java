package org.son.haruharu.domain.matching.business.event;

import java.util.List;

/** 매칭 배치에서 성사되지 못해 EXPIRED·환불된 유저들. 커밋 후 불발 통지 푸시의 트리거. */
public record MatchRefundedEvent(
        List<Long> userIds
) {
}
