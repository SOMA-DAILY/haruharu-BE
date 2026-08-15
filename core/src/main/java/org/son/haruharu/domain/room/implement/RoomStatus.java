package org.son.haruharu.domain.room.implement;

/**
 * 방 라이프사이클 상태(#311).
 * PENDING   = 모집(시작 전). 초대코드 입장을 받는 단계.
 * ACTIVE    = 진행(세션 중, 최종선택 전).
 * SELECTING = 최종선택 phase(서로 선택). 운영진이 선택을 직접 열고/제어해야 할 수 있어 파생이 아닌 명시 상태로 둔다.
 * ENDED     = 종료(읽기전용 아카이브).
 * CANCELLED = 운영진 수동 취소. timestamp 파생과 무관하게 sticky.
 *
 * 설계 문서의 RECRUITING/ONGOING 은 각각 PENDING/ACTIVE 와 동일 개념 — 개명은 admin·demo·테스트 광범위
 * 영향이라 보류하고 의미만 매핑한다([[mvp-pivot-invite-code]]).
 */
public enum RoomStatus {
    PENDING,
    ACTIVE,
    SELECTING,
    ENDED,
    CANCELLED
}
