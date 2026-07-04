package org.son.haruharu.domain.chat.storage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findByChatRoomIdAndDeletedAtIsNullOrderByIdDesc(Long chatRoomId, Pageable pageable);

    List<ChatMessageEntity> findByChatRoomIdAndIdLessThanAndDeletedAtIsNullOrderByIdDesc(
            Long chatRoomId, Long cursorId, Pageable pageable);

    Optional<ChatMessageEntity> findFirstByChatRoomIdAndDeletedAtIsNullOrderByIdDesc(Long chatRoomId);

    long countByChatRoomIdAndIdGreaterThanAndSenderIdNotAndDeletedAtIsNull(
            Long chatRoomId, Long lastReadMessageId, Long excludeSenderId);
}
