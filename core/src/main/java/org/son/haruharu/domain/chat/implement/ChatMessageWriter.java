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

    /** 운영 글 삭제(#457, 멱등). 방송은 app 잡이 나중에 한다(admin 서버엔 STOMP 가 없다). */
    public boolean markAdminDeleted(Long id, java.time.LocalDateTime now) {
        return chatMessageRepository.markAdminDeleted(id, now) > 0;
    }

    public boolean markDeleteBroadcast(Long id, java.time.LocalDateTime now) {
        return chatMessageRepository.markDeleteBroadcast(id, now) > 0;
    }

    /** 데모 정리 전용 하드삭제(#266). */
    public void hardDeleteByChatRoomId(Long chatRoomId) {
        chatMessageRepository.hardDeleteByChatRoomId(chatRoomId);
    }
}
