package org.son.haruharu.domain.chat.implement;

import org.son.haruharu.domain.chat.storage.exception.ChatRoomNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;

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

    /** 소개팅방(roomId)에 GROUP 채팅이 이미 있는지. RoomStartedEvent 재발행 멱등 게이트. */
    public boolean existsByRoomId(Long roomId) {
        return chatRoomRepository.existsByRoomId(roomId);
    }

    /** 소개팅방(roomId)의 GROUP 채팅방(없으면 empty). */
    public Optional<ChatRoom> findByRoomId(Long roomId) {
        return chatRoomRepository.findByRoomId(roomId);
    }

    public Optional<ChatRoom> findByRoomIdForUpdate(Long roomId) {
        return chatRoomRepository.findByRoomIdForUpdate(roomId);
    }

    /** 매칭 결과로 자동 생성된 채팅방 id(없으면 empty). 매칭 결과 화면의 "채팅 시작하기" 직행용. */
    public Optional<Long> findChatRoomIdByMatchResultId(Long matchResultId) {
        return chatRoomRepository.findIdByMatchResultId(matchResultId);
    }
}
