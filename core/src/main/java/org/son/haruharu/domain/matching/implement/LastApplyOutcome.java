package org.son.haruharu.domain.matching.implement;

import java.time.LocalDateTime;

/**
 * 유저의 가장 최근 큐 행 요약. 홈의 직전 신청 결과(불발 노출) 판정에만 쓰인다.
 * resolvedAt = 마지막 상태 전이 시각(EXPIRED면 배치 만료 시각) — matching_queue.updated_at.
 */
public record LastApplyOutcome(MatchingQueueStatus status, LocalDateTime resolvedAt) {
}
