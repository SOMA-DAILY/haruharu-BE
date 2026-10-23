package org.son.haruharu.domain.chat.storage;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatRoomPair;
import org.son.haruharu.domain.chat.implement.ChatRoomPairRepository;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatRoomPairRepositoryImpl implements ChatRoomPairRepository {

    private final ChatRoomPairJpaRepository jpaRepository;

    @Override
    public ChatRoomPair save(ChatRoomPair pair) {
        return jpaRepository.save(ChatRoomPairEntity.from(pair)).toDomain();
    }

    @Override
    public Optional<ChatRoomPair> findByChatRoomId(Long chatRoomId) {
        return jpaRepository.findById(chatRoomId).map(ChatRoomPairEntity::toDomain);
    }

    @Override
    public Optional<ChatRoomPair> findByRoomIdAndUsers(Long roomId, Long lowUserId, Long highUserId) {
        return jpaRepository.findByRoomIdAndLowUserIdAndHighUserId(roomId, lowUserId, highUserId)
                .map(ChatRoomPairEntity::toDomain);
    }

    @Override
    public List<ChatRoomPair> findOpenByRoomId(Long roomId) {
        return jpaRepository.findOpenByRoomId(roomId).stream().map(ChatRoomPairEntity::toDomain).toList();
    }
}
