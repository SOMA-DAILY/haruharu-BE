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

    /** 데모 정리 전용 하드삭제(#266). */
    public void hardDeleteByMatchResultId(Long matchResultId) {
        chatRoomRepository.hardDeleteByMatchResultId(matchResultId);
    }
}
