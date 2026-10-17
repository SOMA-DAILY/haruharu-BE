package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;

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

    public boolean closeGroup(Long roomId, LocalDateTime closeAt) {
        return chatRoomRepository.closeGroupByRoomId(roomId, closeAt);
    }
}
