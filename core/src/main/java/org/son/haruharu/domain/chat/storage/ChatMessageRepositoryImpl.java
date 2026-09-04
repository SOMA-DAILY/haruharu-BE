package org.son.haruharu.domain.chat.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    private final ChatMessageJpaRepository chatMessageJpaRepository;

    @Override
    public ChatMessage save(ChatMessage message) {
        return chatMessageJpaRepository.save(ChatMessageEntity.from(message)).toDomain();
    }

    @Override
    public Optional<ChatMessage> findById(Long id) {
        return chatMessageJpaRepository.findById(id).map(ChatMessageEntity::toDomain);
    }

    @Override
    public List<ChatMessage> findPage(Long chatRoomId, LocalDateTime joinedAt, Long cursorId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<ChatMessageEntity> rows = (cursorId == null)
                ? chatMessageJpaRepository.findByChatRoomIdAndCreatedAtGreaterThanEqualOrderByIdDesc(
                        chatRoomId, joinedAt, pageable)
                : chatMessageJpaRepository
                        .findByChatRoomIdAndIdLessThanAndCreatedAtGreaterThanEqualOrderByIdDesc(
                                chatRoomId, cursorId, joinedAt, pageable);
        return rows.stream().map(ChatMessageEntity::toDomain).toList();
    }

    @Override
    public Optional<ChatMessage> findLast(Long chatRoomId, LocalDateTime joinedAt) {
        return chatMessageJpaRepository
                .findFirstByChatRoomIdAndCreatedAtGreaterThanEqualOrderByIdDesc(chatRoomId, joinedAt)
                .map(ChatMessageEntity::toDomain);
    }

    @Override
    public long countUnread(Long chatRoomId, Long lastReadMessageId,
                            Long excludeSenderId, LocalDateTime joinedAt) {
        // 커서가 없으면(한 번도 안 읽음) 0L 기준 — IDENTITY id는 1부터라 전체가 안읽음으로 집계된다.
        long cursor = (lastReadMessageId == null) ? 0L : lastReadMessageId;
        return chatMessageJpaRepository
                .countByChatRoomIdAndIdGreaterThanAndSenderIdNotAndCreatedAtGreaterThanEqual(
                        chatRoomId, cursor, excludeSenderId, joinedAt);
    }

    @Override
    public long countUnreadByUserId(Long userId) {
        return chatMessageJpaRepository.countUnreadByUserId(userId);
    }

    @Override
    public void hardDeleteByChatRoomId(Long chatRoomId) {
        chatMessageJpaRepository.hardDeleteByChatRoomId(chatRoomId);
    }
}
