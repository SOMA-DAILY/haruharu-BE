package org.son.haruharu.domain.notification.implement;

/**
 * 푸시 수신 설정의 분류 축. 확장 필요 시 상수 + 설정 컬럼 추가로 3분류 이상 가능.
 * - ACTIVITY: 기본 ON(opt-out). 서비스 활동 알림.
 * - MARKETING: 기본 OFF(opt-in). 광고성 정보 — 정보통신망법상 별도 사전동의 필요.
 * - TRANSACTIONAL: 설정과 무관하게 항상 발송(결제완료 등 필수 고지).
 */
public enum NotificationCategory {
    ACTIVITY,
    MARKETING,
    TRANSACTIONAL
}
