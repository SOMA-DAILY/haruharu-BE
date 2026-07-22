package org.son.haruharu.domain.chat.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

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
    public List<ChatMessage> findPage(Long chatRoomId, Long cursorId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<ChatMessageEntity> rows = (cursorId == null)
                ? chatMessageJpaRepository.findByChatRoomIdOrderByIdDesc(chatRoomId, pageable)
                : chatMessageJpaRepository.findByChatRoomIdAndIdLessThanOrderByIdDesc(
                        chatRoomId, cursorId, pageable);
        return rows.stream().map(ChatMessageEntity::toDomain).toList();
    }

    @Override
    public Optional<ChatMessage> findLast(Long chatRoomId) {
        return chatMessageJpaRepository.findFirstByChatRoomIdOrderByIdDesc(chatRoomId)
                .map(ChatMessageEntity::toDomain);
    }

    @Override
    public long countUnread(Long chatRoomId, Long lastReadMessageId, Long excludeSenderId) {
        // 커서가 없으면(한 번도 안 읽음) 0L 기준 — IDENTITY id는 1부터라 전체가 안읽음으로 집계된다.
        long cursor = (lastReadMessageId == null) ? 0L : lastReadMessageId;
        return chatMessageJpaRepository.countByChatRoomIdAndIdGreaterThanAndSenderIdNot(
                chatRoomId, cursor, excludeSenderId);
    }

    @Override
    public void hardDeleteByChatRoomId(Long chatRoomId) {
        chatMessageJpaRepository.hardDeleteByChatRoomId(chatRoomId);
    }
}
