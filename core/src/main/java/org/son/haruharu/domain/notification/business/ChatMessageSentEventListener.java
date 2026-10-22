package org.son.haruharu.domain.notification.business;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.config.ChatFeatureProperties;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.notification.implement.PushBundle;
import org.son.haruharu.infrastructure.fcm.PushStyle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ChatMessageSentEventListener {

    private final PushSendService pushSendService;
    private final BlockReader blockReader;
    private final ChatDeliveryService chatDeliveryService;
    private final ChatFeatureProperties chatFeatureProperties;
    private final PushBundleService pushBundleService;

    public ChatMessageSentEventListener(PushSendService pushSendService, BlockReader blockReader,
                                       ChatDeliveryService chatDeliveryService,
                                       ChatFeatureProperties chatFeatureProperties,
                                       PushBundleService pushBundleService) {
        this.pushSendService = pushSendService;
        this.blockReader = blockReader;
        this.chatDeliveryService = chatDeliveryService;
        this.chatFeatureProperties = chatFeatureProperties;
        this.pushBundleService = pushBundleService;
    }

    // 커밋 후 발송 — 메시지 저장 트랜잭션이 푸시 실패로 롤백되지 않도록
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatMessageSentEvent event) {
        if (event.recipientUserIds().isEmpty()) {
            return;
        }
        chatDeliveryService.deliverIfActive(event.chatRoomId(), () -> {
            // 발신자를 차단한 수신자는 제외한다(#408, 수신자별 단건 조회 방지).
            Set<Long> blockers = blockReader.getBlockerUserIds(event.senderUserId());
            List<Long> recipients = event.recipientUserIds().stream()
                    .filter(id -> !blockers.contains(id))
                    .toList();
            if (recipients.isEmpty()) {
                return;
            }
            Map<String, String> data = Map.of("chatRoomId", event.chatRoomId().toString());
            // 답장(#456): 플래그 ON 이면 원문 주인은 CHAT 대신 CHAT_REPLY 1건만 받는다(같은 사람에게 두 번 안 감).
            // 원문 주인이 수신자가 아니면(본인 답장, 차단, 운영 글 시스템 유저) 답장 푸시는 없다.
            Long replyTarget = event.replyToSenderUserId();
            boolean replyPush = chatFeatureProperties.replyPushEnabled() && replyTarget != null
                    && recipients.contains(replyTarget);
            List<Long> chatRecipients = replyPush
                    ? recipients.stream().filter(id -> !id.equals(replyTarget)).toList()
                    : recipients;
            if (replyPush) {
                pushSendService.sendToUsers(List.of(replyTarget), NotificationType.CHAT_REPLY, data,
                        event.senderNickname());
            }
            if (chatRecipients.isEmpty()) {
                return;
            }
            if (event.chatRoomType() == ChatRoomType.GROUP) {
                pushGroupChat(event, chatRecipients, data);
            } else if (event.photo()) {
                pushSendService.saveInbox(chatRecipients, NotificationType.CHAT, data, event.senderNickname());
                pushSendService.push(chatRecipients, NotificationType.CHAT, data, NotificationType.CHAT.title(),
                        event.senderNickname() + "님이 사진을 보냈어요", PushStyle.NORMAL);
            } else {
                pushSendService.sendToUsers(chatRecipients, NotificationType.CHAT, data, event.senderNickname());
            }
        });
    }

    /**
     * 단체 채팅(#505): 알림함엔 메시지마다 남기고, 푸시는 방마다 한 칸으로 묶는다.
     * 울릴 사람은 보통으로, 나머지는 같은 칸을 "새 메시지 N개"로 조용히 교체한다. 묶음 판단이 실패하면 지금처럼 모두 울린다.
     */
    private void pushGroupChat(ChatMessageSentEvent event, List<Long> recipients, Map<String, String> data) {
        pushSendService.saveInbox(recipients, NotificationType.CHAT, data, event.senderNickname());
        String body = event.photo() ? event.senderNickname() + "님이 사진을 보냈어요"
                : String.format(NotificationType.CHAT.body(), event.senderNickname());
        String key = PushBundle.chatKey(event.chatRoomId());
        List<PushBundleService.ChatDecision> decisions;
        try {
            decisions = pushBundleService.decideGroupChat(event.chatRoomId(), event.chatMessageId(), recipients);
        } catch (Exception e) {
            log.error("채팅 푸시 묶음 판단 실패, 보통 알림으로 발송: chatRoomId={}", event.chatRoomId(), e);
            pushSendService.push(recipients, NotificationType.CHAT, data,
                    NotificationType.CHAT.title(), body, PushStyle.alert(key));
            return;
        }
        // 같은 문구·표시 방식끼리 한 번에 보낸다(안 읽은 수가 같은 사람끼리).
        Map<PushGroup, List<Long>> groups = decisions.stream().collect(Collectors.groupingBy(
                d -> new PushGroup(d.alert(), d.unreadCount()),
                Collectors.mapping(PushBundleService.ChatDecision::userId, Collectors.toList())));
        groups.forEach((group, userIds) -> pushSendService.push(userIds, NotificationType.CHAT, data,
                PushBundle.chatTitle(group.unreadCount()), body,
                group.alert() ? PushStyle.alert(key) : PushStyle.quiet(key)));
    }

    private record PushGroup(boolean alert, long unreadCount) {
    }
}
