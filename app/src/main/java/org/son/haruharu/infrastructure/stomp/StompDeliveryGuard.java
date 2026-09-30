package org.son.haruharu.infrastructure.stomp;

/**
 * 다른 인스턴스가 발행한 전달을 로컬로 넘기기 직전에 조건을 다시 확인한다(#453).
 * <p>
 * 발행 인스턴스는 조건을 확인한 뒤 발행하지만, 수신 인스턴스에 도착하기 전에 조건이 깨질 수 있다
 * (예: 메시지 발행 후 다른 인스턴스에서 방이 닫혀 종료 알림이 먼저 도착). guard 는 발행자가 정한 불투명 키이고,
 * 해석은 그 조건을 아는 도메인 쪽 구현이 한다 — 배달 계층은 도메인을 모른다.
 */
public interface StompDeliveryGuard {

    /** guard 조건이 아직 유효하면 true. false 면 수신 인스턴스는 전달을 버린다. */
    boolean allows(String guard);
}
