package org.son.haruharu.infrastructure.stomp;

import tools.jackson.databind.JsonNode;

/**
 * 인스턴스 간 STOMP 팬아웃 메시지(#453). 수신 인스턴스는 kind 에 따라 로컬 전달만 한다.
 * <p>
 * origin 은 발행 인스턴스 id — 발행 인스턴스는 이미 로컬 전달했으므로 되돌아온 자기 메시지를 버린다.
 * guard 는 선택 — 있으면 수신 인스턴스가 전달 직전에 {@link StompDeliveryGuard}로 다시 확인한다.
 * payload 는 발행 시 Boot JsonMapper 로 만든 JSON 트리 — 수신 측은 해석하지 않고 그대로 넘긴다.
 */
public record StompFanoutEnvelope(String origin, Kind kind, String destination, String userId, String guard,
                                  JsonNode payload) {

    public enum Kind { TOPIC, USER }
}
