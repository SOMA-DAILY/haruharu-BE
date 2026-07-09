package org.son.haruharu.domain.chat.storage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {

    // 삭제된(deleted_at) 메시지 제외는 BaseEntity @SoftDelete 전역 필터가 처리한다.
    List<ChatMessageEntity> findByChatRoomIdOrderByIdDesc(Long chatRoomId, Pageable pageable);

    List<ChatMessageEntity> findByChatRoomIdAndIdLessThanOrderByIdDesc(
            Long chatRoomId, Long cursorId, Pageable pageable);

    Optional<ChatMessageEntity> findFirstByChatRoomIdOrderByIdDesc(Long chatRoomId);

    long countByChatRoomIdAndIdGreaterThanAndSenderIdNot(
            Long chatRoomId, Long lastReadMessageId, Long excludeSenderId);
}
