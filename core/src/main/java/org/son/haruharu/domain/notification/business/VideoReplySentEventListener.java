package org.son.haruharu.domain.notification.business;

import lombok.RequiredArgsConstructor;

import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.son.haruharu.domain.chat.business.event.VideoReplySentEvent;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.user.business.UserService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VideoReplySentEventListener {
    private final PushSendService pushSendService;
    private final ChatDeliveryService delivery;
    private final BlockReader blocks;
    private final UserService users;
    private final ChatRoomMemberReader members;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(VideoReplySentEvent event) {
        delivery.deliverIfActive(
                event.chatRoomId(),
                () -> {
                    if (users.isWithdrawn(event.ownerId())) return;
                    if (blocks.isBlockedBetween(event.senderId(), event.ownerId())) return;
                    if (members.findMembership(event.chatRoomId(), event.ownerId())
                            .filter(m -> !m.hasLeft())
                            .isEmpty()) return;
                    if (members.findMembership(event.chatRoomId(), event.senderId())
                            .filter(m -> !m.hasLeft()).isEmpty()) return;
                    pushSendService.sendToUsers(
                            List.of(event.ownerId()),
                            NotificationType.VIDEO_REPLY,
                            Map.of("chatRoomId", event.chatRoomId().toString(), "sender_user_id", event.senderId().toString()),
                            event.senderName());
                });
    }
}
