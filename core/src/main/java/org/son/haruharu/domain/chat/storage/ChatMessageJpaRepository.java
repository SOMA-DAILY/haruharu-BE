package org.son.haruharu.domain.chat.storage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // 네이티브 DELETE — 전역 @SoftDelete 우회(데모 재시드 시 이전 대화가 누적되지 않게 물리 삭제).
    @Modifying
    @Query(value = "delete from chat_messages where chat_room_id = :chatRoomId", nativeQuery = true)
    void hardDeleteByChatRoomId(@Param("chatRoomId") Long chatRoomId);
}
