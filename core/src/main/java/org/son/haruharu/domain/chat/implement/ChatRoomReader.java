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

    /** 매칭 결과로 자동 생성된 채팅방 id(없으면 empty). 매칭 결과 화면의 "채팅 시작하기" 직행용. */
    public Optional<Long> findChatRoomIdByMatchResultId(Long matchResultId) {
        return chatRoomRepository.findIdByMatchResultId(matchResultId);
    }
}
