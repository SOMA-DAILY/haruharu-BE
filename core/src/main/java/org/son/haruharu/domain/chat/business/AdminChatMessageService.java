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
 * 시스템 유저('하루')는 채팅방 멤버가 아니라 멤버십 검증이 없고, ChatMessageSentEvent 대신 ChatMessageBroadcastEvent 만 발행한다
 * (참가자 CHAT 푸시·미션 집계 제외). 리스너가 AFTER_COMMIT 이라 트랜잭션 안에서만 부른다.
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
     * 운영자가 지웠지만 아직 알리지 않은 메시지를 MESSAGE_DELETED 로 방송하고 delete_broadcast_at 을 찍는다(조건부 UPDATE 로 1회).
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
