package org.son.haruharu.domain.room.business.event;

/**
 * 최종선택 접수가 열렸을 때(#320). reconciler 가 방의 final_selection_opens_at 을 지나 SELECTING 으로
 * 전이시킨 순간 1회 발행한다. 알림 발송은 notification 도메인 리스너가 AFTER_COMMIT 으로 받는다
 * — room 이 notification 에 결합하지 않도록 이벤트로 끊는다(RoomStartedEvent 와 동일 패턴).
 */
public record FinalSelectionOpenedEvent(Long roomId) {
}
