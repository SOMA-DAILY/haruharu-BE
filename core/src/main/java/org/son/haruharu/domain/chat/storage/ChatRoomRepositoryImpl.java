package org.son.haruharu.domain.chat.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatRoomRepositoryImpl implements ChatRoomRepository {

    private final ChatRoomJpaRepository chatRoomJpaRepository;

    @Override
    public ChatRoom save(ChatRoom chatRoom) {
        return chatRoomJpaRepository.save(ChatRoomEntity.from(chatRoom)).toDomain();
    }

    @Override
    public Optional<ChatRoom> findById(Long id) {
        return chatRoomJpaRepository.findById(id).map(ChatRoomEntity::toDomain);
    }

    @Override
    public boolean existsByMatchResultId(Long matchResultId) {
        return chatRoomJpaRepository.existsByMatchResultId(matchResultId);
    }

    @Override
    public Optional<Long> findIdByMatchResultId(Long matchResultId) {
        return chatRoomJpaRepository.findByMatchResultId(matchResultId).map(entity -> entity.toDomain().id());
    }
}
