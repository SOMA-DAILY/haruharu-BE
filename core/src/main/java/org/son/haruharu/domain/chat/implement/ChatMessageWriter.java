package org.son.haruharu.domain.chat.implement;

import org.springframework.stereotype.Component;

@Component
public class ChatMessageWriter {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMessageWriter(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public ChatMessage save(ChatMessage message) {
        return chatMessageRepository.save(message);
    }

    public boolean markAdminDeleted(Long id) { return chatMessageRepository.markAdminDeleted(id); }

    /** 데모 정리 전용 하드삭제(#266). */
    public void hardDeleteByChatRoomId(Long chatRoomId) {
        chatMessageRepository.hardDeleteByChatRoomId(chatRoomId);
    }
}
