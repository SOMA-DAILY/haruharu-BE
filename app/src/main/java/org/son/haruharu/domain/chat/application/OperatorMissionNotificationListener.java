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
    /** ADMIN_MESSAGE 템플릿(#457)은 제목·본문을 인자로 받는다. 미션 카드는 이 문구로 고정. */
    static final String PUSH_TITLE = "🎬 하루의 새 미션";
    static final String PUSH_BODY = "운영자가 보낸 미션을 확인하고 하루를 남겨보세요";

    private final PushSendService pushSendService;
    private final ChatDeliveryService chatDeliveryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OperatorMissionMessageSentEvent event) {
        chatDeliveryService.deliverIfActive(event.chatRoomId(), () ->
                pushSendService.sendToUsers(event.recipients(), NotificationType.ADMIN_MESSAGE,
                        Map.of("chatRoomId", event.chatRoomId().toString(), "missionId", event.missionId().toString()),
                        PUSH_TITLE, PUSH_BODY));
    }
}
