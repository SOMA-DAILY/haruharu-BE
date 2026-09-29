package org.son.haruharu.domain.chat.business;

import org.son.haruharu.config.ChatFeatureProperties;
import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.business.event.ChatMessageBroadcastEvent;
import org.son.haruharu.domain.chat.business.event.MessageDeletedEvent;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatMessageWriter;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 운영 글(#457, F3). app 서버의 편성 발송 잡만 부른다.
 * 시스템 유저('하루')는 채팅방 멤버가 아니므로 멤버십 검증을 하지 않는다. ChatMessageSentEvent 를 발행하지 않아
 * 참가자용 CHAT 푸시와 미션 활동 집계에 섞이지 않는다 — 브로드캐스트(ChatMessageBroadcastEvent)만 발행한다.
 * 이벤트는 @TransactionalEventListener(AFTER_COMMIT)라 반드시 트랜잭션 안에서 발행해야 한다.
 */
@Service
@Transactional(readOnly = true)
public class AdminChatMessageService {

    private final ChatRoomReader chatRoomReader;
    private final ChatMessageReader chatMessageReader;
    private final ChatMessageWriter chatMessageWriter;
    private final ChatFeatureProperties chatFeatureProperties;
    private final ApplicationEventPublisher eventPublisher;

    public AdminChatMessageService(ChatRoomReader chatRoomReader, ChatMessageReader chatMessageReader,
                                   ChatMessageWriter chatMessageWriter, ChatFeatureProperties chatFeatureProperties,
                                   ApplicationEventPublisher eventPublisher) {
        this.chatRoomReader = chatRoomReader;
        this.chatMessageReader = chatMessageReader;
        this.chatMessageWriter = chatMessageWriter;
        this.chatFeatureProperties = chatFeatureProperties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ChatMessage sendAsSystem(Long chatRoomId, String content, Long scheduleItemId) {
        Long systemUserId = chatFeatureProperties.systemUserId();
        if (systemUserId == null) {
            throw new IllegalStateException("haruharu.chat.system-user-id(CHAT_SYSTEM_USER_ID) 가 설정되지 않았습니다");
        }
        chatRoomReader.lockForUpdate(chatRoomId); // 유저 메시지와 id 순서를 맞춘다
        ChatMessage saved = chatMessageWriter.save(ChatMessage.admin(chatRoomId, systemUserId, content, scheduleItemId));
        eventPublisher.publishEvent(new ChatMessageBroadcastEvent(ChatMessageBroadcast.from(saved)));
        return saved;
    }

    /**
     * 운영자가 지웠지만 아직 알리지 않은 메시지를 MESSAGE_DELETED 로 방송하고 delete_broadcast_at 을 찍는다.
     * admin 서버엔 STOMP 가 없어서 app 잡이 대신 한다. 인스턴스 2개가 겹쳐도 조건부 UPDATE 로 한 번만 발행한다.
     */
    @Transactional
    public int broadcastPendingDeletes(int limit, LocalDateTime now) {
        int count = 0;
        for (ChatMessage message : chatMessageReader.findPendingDeleteBroadcasts(limit)) {
            if (chatMessageWriter.markDeleteBroadcast(message.id(), now)) {
                eventPublisher.publishEvent(new MessageDeletedEvent(message.chatRoomId(), message.id()));
                count++;
            }
        }
        return count;
    }
}
