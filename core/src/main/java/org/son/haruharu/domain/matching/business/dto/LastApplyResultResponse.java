package org.son.haruharu.domain.matching.business.dto;

import java.time.LocalDateTime;

/**
 * 홈의 직전 신청 불발 결과. 22:00 배치에서 미매칭되어 EXPIRED로 만료·환불된 건을 노출한다.
 * outcome은 문자열 enum(현재 스코프 "EXPIRED"만) — 향후 확장 여지.
 * expiredAt = 만료 시각, refundAmount = 환불된 참가 구름 수(enterCost).
 * (필드명은 전역 SNAKE_CASE 직렬화로 outcome/expired_at/refund_amount 계약을 만든다.)
 */
public record LastApplyResultResponse(String outcome, LocalDateTime expiredAt, long refundAmount) {
}
