package org.son.haruharu.domain.chat.business;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.business.dto.ChatPhotoView;
import org.son.haruharu.domain.chat.business.event.ChatMessageBroadcastEvent;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.chat.business.exception.ChatMessageNotFoundException;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.business.exception.ChatPhotoRequestConflictException;
import org.son.haruharu.domain.chat.business.exception.PairChatClosedException;
import org.son.haruharu.domain.chat.implement.*;
import org.son.haruharu.domain.chat.implement.exception.InvalidChatPhotoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatPhotoService {
    private final ChatRoomReader rooms;
    private final ChatRoomMemberReader members;
    private final ChatMessageReader messages;
    private final ChatMessageWriter writer;
    private final ChatParticipantPolicy participants;
    private final ChatRoomLifecyclePolicy lifecycle;
    private final ChatPhotoStore photos;
    private final BlockReader blocks;
    private final ApplicationEventPublisher events;
    private final PairChatSchedule pairChatSchedule;
    private final Clock clock;

    /** 한 장 한 메시지. 방 잠금으로 나가기·동시 재시도와 직렬화하고 커밋 후에만 방송한다. */
    @Transactional
    public ChatMessageBroadcast send(Long chatId, Long senderId, String requestId, byte[] bytes, String contentType) {
        requireRequestId(requestId);
        rooms.lockForUpdate(chatId);
        requireAccess(chatId, senderId);
        ChatRoom chat = rooms.getById(chatId);
        ChatPhotoFile file = ChatPhotoFile.validate(bytes, contentType);
        var previous = messages.findByRequest(chatId, senderId, requestId);
        if (previous.isPresent()) {
            ChatMessage message = previous.get();
            if (message.photo() == null || !message.photo().digest().equals(photos.digest(bytes)))
                throw new ChatPhotoRequestConflictException();
            return broadcast(message);
        }
        lifecycle.requireActive(chat);
        // 대화 시간 1:1(#540)은 글과 같이 사진도 운영자가 정한 시간에만 보낸다.
        if (chat.type() == ChatRoomType.PAIR && !pairChatSchedule.windowAt(chat.roomId(), LocalDateTime.now(clock)).isOpen())
            throw new PairChatClosedException();
        ChatPhoto photo = photos.upload(chatId, bytes, file);
        ChatMessage saved = writer.save(ChatMessage.photo(chatId, senderId, requestId, photo));
        var recipients = members.findByChatRoomId(chatId).stream().filter(m -> !m.hasLeft())
                .map(ChatRoomMember::userId).filter(id -> !id.equals(senderId)).toList();
        events.publishEvent(new ChatMessageSentEvent(saved.id(), chatId, chat.type(), chat.roomId(), senderId,
                recipients, participants.displayName(chat, senderId), saved.createdAt(), null, true));
        ChatMessageBroadcast result = broadcast(saved);
        // 구독 방송에는 조회 주소를 넣지 않는다. 각 수신자가 GET에서 자기 열람 권한을 확인한다.
        events.publishEvent(new ChatMessageBroadcastEvent(ChatMessageBroadcast.from(saved).withPhoto(
                new ChatPhotoView(photo.key(), null, photo.width(), photo.height()), saved.clientRequestId())));
        return result;
    }

    public ChatPhotoView get(Long chatId, Long messageId, Long viewerId) {
        requireAccess(chatId, viewerId);
        ChatMessage message = messages.findById(messageId)
                .filter(m -> m.chatRoomId().equals(chatId) && !m.isAdminDeleted() && m.photo() != null)
                .filter(m -> !blocks.getBlockedUserIds(viewerId).contains(m.senderId()))
                .orElseThrow(ChatMessageNotFoundException::new);
        return photos.view(message.photo());
    }

    private ChatMessageBroadcast broadcast(ChatMessage message) {
        return ChatMessageBroadcast.from(message)
                .withPhoto(message.isAdminDeleted() ? null : photos.view(message.photo()), message.clientRequestId());
    }

    private void requireAccess(Long chatId, Long userId) {
        members.findActiveMembership(chatId, userId).orElseThrow(ChatNotMemberException::new);
        rooms.getById(chatId);
        participants.requireActiveIfOneToOne(chatId, userId);
    }

    private void requireRequestId(String requestId) {
        try {
            if (requestId == null || !UUID.fromString(requestId).toString().equals(requestId))
                throw new InvalidChatPhotoException();
        } catch (IllegalArgumentException invalid) {
            throw new InvalidChatPhotoException();
        }
    }
}
