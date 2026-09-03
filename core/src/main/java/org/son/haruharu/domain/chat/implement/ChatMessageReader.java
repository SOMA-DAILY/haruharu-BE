package org.son.haruharu.domain.chat.implement;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class ChatMessageReader {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMessageReader(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public List<ChatMessage> findPage(Long chatRoomId, LocalDateTime joinedAt, Long cursorId, int limit) {
        return chatMessageRepository.findPage(chatRoomId, joinedAt, cursorId, limit);
    }

    public Optional<ChatMessage> findLast(Long chatRoomId, LocalDateTime joinedAt) {
        return chatMessageRepository.findLast(chatRoomId, joinedAt);
    }

    public long countUnread(Long chatRoomId, Long lastReadMessageId,
                            Long excludeSenderId, LocalDateTime joinedAt) {
        return chatMessageRepository.countUnread(
                chatRoomId, lastReadMessageId, excludeSenderId, joinedAt);
    }

    public long countUnreadByUserId(Long userId) {
        return chatMessageRepository.countUnreadByUserId(userId);
    }
}
