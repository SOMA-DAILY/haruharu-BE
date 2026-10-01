package org.son.haruharu.domain.chat.implement;

import org.springframework.stereotype.Component;

@Component
public class ChatRoomWriter {

    private final ChatRoomRepository chatRoomRepository;

    public ChatRoomWriter(ChatRoomRepository chatRoomRepository) {
        this.chatRoomRepository = chatRoomRepository;
    }

    public ChatRoom save(ChatRoom chatRoom) {
        return chatRoomRepository.save(chatRoom);
    }

    public void delete(Long id) {
        chatRoomRepository.deleteById(id);
    }
}
