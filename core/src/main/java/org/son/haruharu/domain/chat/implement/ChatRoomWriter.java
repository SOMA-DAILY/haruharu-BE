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

    /** 원본 방의 열린 PAIR 채팅을 모두 닫는다. 닫은 개수를 돌려준다. */
    public int closePairs(Long roomId, LocalDateTime closeAt) {
        return chatRoomRepository.closePairsByRoomId(roomId, closeAt);
    }
}
