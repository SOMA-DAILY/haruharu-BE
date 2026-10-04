package org.son.haruharu.domain.chat.application;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.son.haruharu.domain.chat.business.event.OperatorMissionMessageSentEvent;
import org.son.haruharu.domain.notification.business.PushSendService;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OperatorMissionNotificationListener {
    private final PushSendService pushSendService;
    private final ChatDeliveryService chatDeliveryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OperatorMissionMessageSentEvent event) {
        chatDeliveryService.deliverIfActive(event.chatRoomId(), () ->
                pushSendService.sendToUsers(event.recipients(), NotificationType.ADMIN_MESSAGE,
                        Map.of("chatRoomId", event.chatRoomId().toString(), "missionId", event.missionId().toString())));
    }
}
