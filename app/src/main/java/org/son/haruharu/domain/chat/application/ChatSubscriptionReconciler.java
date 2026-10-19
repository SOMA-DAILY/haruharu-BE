package org.son.haruharu.domain.chat.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.son.haruharu.infrastructure.stomp.ChatSubscriptionCloser;
import org.son.haruharu.infrastructure.stomp.ChatSubscriptionRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 인스턴스마다 실행: Batch 승자 여부·Redis 유실·저장 상태 갱신과 무관하게 잔존 구독을 복구한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSubscriptionReconciler {
    private final ChatSubscriptionRegistry registry;
    private final ChatSubscriptionCloser closer;
    private final ChatDeliveryService delivery;

    @Scheduled(fixedDelayString = "${haruharu.chat.subscription-reconcile-delay-ms:10000}")
    public void reconcile() {
        for (Long id : registry.roomIds()) {
            try {
                if (!delivery.isActive(id)) {
                    closer.close(id, new ChatLifecycleEventListener.RoomClosedPayload("CHAT_ROOM_CLOSED", id));
                }
            } catch (RuntimeException e) {
                log.warn("종료 채팅 구독 정리 실패: chatRoomId={}", id, e);
            }
        }
    }
}
