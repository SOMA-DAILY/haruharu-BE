package org.son.haruharu.domain.chat.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    private final ChatMessageJpaRepository chatMessageJpaRepository;

    @Override
    public Optional<ChatMessage> findByRequest(Long chatRoomId, Long senderId, String requestId) {
        return chatMessageJpaRepository.findByChatRoomIdAndSenderIdAndClientRequestId(chatRoomId, senderId, requestId).map(ChatMessageEntity::toDomain);
    }

    @Override
    public ChatMessage save(ChatMessage message) {
        return chatMessageJpaRepository.save(ChatMessageEntity.from(message)).toDomain();
    }

    @Override
    public Optional<ChatMessage> findById(Long id) {
        return chatMessageJpaRepository.findById(id).map(ChatMessageEntity::toDomain);
    }

    @Override
    public List<ChatMessage> findAllByIds(java.util.Collection<Long> ids) {
        return chatMessageJpaRepository.findAllById(ids).stream().map(ChatMessageEntity::toDomain).toList();
    }

    @Override
    public List<ChatMessage> findPage(Long chatRoomId, Long cursorId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<ChatMessageEntity> rows = (cursorId == null)
                ? chatMessageJpaRepository.findByChatRoomIdOrderByIdDesc(chatRoomId, pageable)
                : chatMessageJpaRepository.findByChatRoomIdAndIdLessThanOrderByIdDesc(
                        chatRoomId, cursorId, pageable);
        return rows.stream().map(ChatMessageEntity::toDomain).toList();
    }

    @Override
    public Optional<ChatMessage> findLast(Long chatRoomId, Set<Long> excludeSenderIds) {
        Optional<ChatMessageEntity> row = excludeSenderIds.isEmpty()
                ? chatMessageJpaRepository.findFirstByChatRoomIdOrderByIdDesc(chatRoomId)
                : chatMessageJpaRepository.findFirstByChatRoomIdAndSenderIdNotInOrderByIdDesc(
                        chatRoomId, excludeSenderIds);
        return row.map(ChatMessageEntity::toDomain);
    }

    @Override
    public long countUnread(Long chatRoomId, Long lastReadMessageId, Set<Long> excludeSenderIds) {
        // 커서가 없으면(한 번도 안 읽음) 0L 기준 — IDENTITY id는 1부터라 전체가 안읽음으로 집계된다.
        long cursor = (lastReadMessageId == null) ? 0L : lastReadMessageId;
        return chatMessageJpaRepository.countByChatRoomIdAndIdGreaterThanAndSenderIdNotIn(
                chatRoomId, cursor, excludeSenderIds);
    }

    @Override
    public int markAdminDeleted(Long id, java.time.LocalDateTime now) {
        return chatMessageJpaRepository.markAdminDeleted(id, now);
    }

    @Override
    public List<ChatMessage> findPendingDeleteBroadcasts(int limit) {
        return chatMessageJpaRepository.findPendingDeleteBroadcasts(PageRequest.of(0, limit)).stream()
                .map(ChatMessageEntity::toDomain).toList();
    }

    @Override
    public int markDeleteBroadcast(Long id, java.time.LocalDateTime now) {
        return chatMessageJpaRepository.markDeleteBroadcast(id, now);
    }
}
