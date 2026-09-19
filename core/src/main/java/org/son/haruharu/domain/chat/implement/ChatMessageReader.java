package org.son.haruharu.domain.chat.implement;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class ChatMessageReader {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMessageReader(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public Optional<ChatMessage> findByRequest(Long chatRoomId, Long senderId, String requestId) {
        return chatMessageRepository.findByRequest(chatRoomId, senderId, requestId);
    }

    public List<ChatMessage> findPage(Long chatRoomId, Long cursorId, int limit) {
        return chatMessageRepository.findPage(chatRoomId, cursorId, limit);
    }

    public Optional<ChatMessage> findLast(Long chatRoomId, Set<Long> excludeSenderIds) {
        return chatMessageRepository.findLast(chatRoomId, excludeSenderIds);
    }

    public long countUnread(Long chatRoomId, Long lastReadMessageId, Set<Long> excludeSenderIds) {
        return chatMessageRepository.countUnread(chatRoomId, lastReadMessageId, excludeSenderIds);
    }
}
