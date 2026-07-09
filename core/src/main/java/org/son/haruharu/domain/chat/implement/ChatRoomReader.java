package org.son.haruharu.domain.chat.implement;

import org.son.haruharu.domain.chat.storage.exception.ChatRoomNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class ChatRoomReader {

    private final ChatRoomRepository chatRoomRepository;

    public ChatRoomReader(ChatRoomRepository chatRoomRepository) {
        this.chatRoomRepository = chatRoomRepository;
    }

    public ChatRoom getById(Long id) {
        return chatRoomRepository.findById(id)
                .orElseThrow(ChatRoomNotFoundException::new);
    }

    public boolean existsByMatchResultId(Long matchResultId) {
        return chatRoomRepository.existsByMatchResultId(matchResultId);
    }
}
