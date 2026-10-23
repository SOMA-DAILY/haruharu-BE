package org.son.haruharu.domain.chat.implement;

import java.util.List;
import java.util.Optional;
import org.son.haruharu.domain.chat.storage.exception.ChatRoomNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class ChatRoomPairReader {

    private final ChatRoomPairRepository repository;

    public ChatRoomPairReader(ChatRoomPairRepository repository) {
        this.repository = repository;
    }

    public Optional<ChatRoomPair> findByChatRoomId(Long chatRoomId) {
        return repository.findByChatRoomId(chatRoomId);
    }

    /** PAIR 채팅방의 쌍 정보. 없으면 채팅방이 없는 것으로 본다. */
    public ChatRoomPair getByChatRoomId(Long chatRoomId) {
        return repository.findByChatRoomId(chatRoomId).orElseThrow(ChatRoomNotFoundException::new);
    }

    public Optional<ChatRoomPair> findByRoomIdAndUsers(Long roomId, Long userA, Long userB) {
        return repository.findByRoomIdAndUsers(roomId, Math.min(userA, userB), Math.max(userA, userB));
    }

    public List<ChatRoomPair> findOpenByRoomId(Long roomId) {
        return repository.findOpenByRoomId(roomId);
    }
}
