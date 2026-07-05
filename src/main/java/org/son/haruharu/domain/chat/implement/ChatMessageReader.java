package org.son.haruharu.domain.chat.implement;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ChatMessageReader {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMessageReader(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public List<ChatMessage> findPage(Long chatRoomId, Long cursorId, int limit) {
        return chatMessageRepository.findPage(chatRoomId, cursorId, limit);
    }

    public Optional<ChatMessage> findLast(Long chatRoomId) {
        return chatMessageRepository.findLast(chatRoomId);
    }

    public long countUnread(Long chatRoomId, Long lastReadMessageId, Long excludeSenderId) {
        return chatMessageRepository.countUnread(chatRoomId, lastReadMessageId, excludeSenderId);
    }
}
