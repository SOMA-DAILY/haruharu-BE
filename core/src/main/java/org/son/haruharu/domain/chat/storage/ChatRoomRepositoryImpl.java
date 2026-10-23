package org.son.haruharu.domain.chat.storage;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomRepository;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.springframework.stereotype.Repository;

import java.util.List;
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
    public Optional<Long> lockByIdIncludingDeleted(Long id) {
        return chatRoomJpaRepository.lockByIdIncludingDeleted(id);
    }

    @Override
    public Optional<Long> lockByIdForShare(Long id) {
        return chatRoomJpaRepository.findByIdForShare(id).map(entity -> entity.toDomain().id());
    }

    @Override
    public boolean existsIncludingDeletedByMatchResultId(Long matchResultId) {
        return chatRoomJpaRepository.existsIncludingDeletedByMatchResultId(matchResultId);
    }

    @Override
    public void deleteById(Long id) {
        chatRoomJpaRepository.deleteById(id);
    }

    @Override
    public boolean existsByMatchResultId(Long matchResultId) {
        return chatRoomJpaRepository.existsByMatchResultId(matchResultId);
    }

    @Override
    public Optional<Long> findIdByMatchResultId(Long matchResultId) {
        return chatRoomJpaRepository.findByMatchResultId(matchResultId).map(entity -> entity.toDomain().id());
    }

    @Override
    public boolean existsByRoomId(Long roomId) {
        return chatRoomJpaRepository.existsByRoomIdAndType(roomId, ChatRoomType.GROUP);
    }

    @Override
    public Optional<ChatRoom> findByRoomId(Long roomId) {
        return chatRoomJpaRepository.findByRoomIdAndType(roomId, ChatRoomType.GROUP).map(ChatRoomEntity::toDomain);
    }

    @Override
    public Optional<Long> findIdByRoomId(Long roomId) {
        return chatRoomJpaRepository.findIdByRoomIdAndType(roomId, ChatRoomType.GROUP);
    }

    @Override
    public Optional<ChatRoom> findPairByRoomIdAndUsers(Long roomId, Long userA, Long userB) {
        return chatRoomJpaRepository.findPairByRoomIdAndUsers(roomId, userA, userB).map(ChatRoomEntity::toDomain);
    }

    @Override
    public List<ChatRoom> findOpenPairsByRoomId(Long roomId) {
        return chatRoomJpaRepository.findOpenByRoomIdAndType(roomId, ChatRoomType.PAIR).stream()
                .map(ChatRoomEntity::toDomain).toList();
    }

    @Override
    public boolean closeGroupByRoomId(Long roomId, LocalDateTime closeAt) {
        return chatRoomJpaRepository.closeGroupByRoomId(roomId, closeAt) > 0;
    }

    @Override
    public int closePairsByRoomId(Long roomId, LocalDateTime closeAt) {
        return chatRoomJpaRepository.closePairsByRoomId(roomId, closeAt);
    }
}
