package org.son.haruharu.domain.chat.application;

import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.son.haruharu.infrastructure.stomp.StompDeliveryGuard;
import org.springframework.stereotype.Component;

/**
 * 채팅방 메시지 팬아웃의 수신 측 재확인(#453). 다른 인스턴스가 발행한 방 메시지를 로컬 전달하기 직전에 방이 아직
 * 활성인지 본다 — 발행 인스턴스는 {@code deliverIfActive} 로 확인했지만, 도착 전에 다른 인스턴스에서 방이 닫혀
 * 종료 알림이 먼저 갔을 수 있다. 종료 알림 자체는 guard 없이 보낸다.
 */
@Component
public class ChatRoomDeliveryGuard implements StompDeliveryGuard {

    private static final String ACTIVE_ROOM_PREFIX = "chat-room-active:";

    private final ChatDeliveryService chatDeliveryService;

    public ChatRoomDeliveryGuard(ChatDeliveryService chatDeliveryService) {
        this.chatDeliveryService = chatDeliveryService;
    }

    /** 방이 활성일 때만 전달하라는 guard 키. */
    public static String activeRoom(Long chatRoomId) {
        return ACTIVE_ROOM_PREFIX + chatRoomId;
    }

    /** 모르는 guard(다른 버전 인스턴스가 추가한 조건 등)는 막지 않는다 — guard 도입 전과 같은 동작. */
    @Override
    public boolean allows(String guard) {
        if (!guard.startsWith(ACTIVE_ROOM_PREFIX)) {
            return true;
        }
        return chatDeliveryService.isActive(Long.valueOf(guard.substring(ACTIVE_ROOM_PREFIX.length())));
    }
}
