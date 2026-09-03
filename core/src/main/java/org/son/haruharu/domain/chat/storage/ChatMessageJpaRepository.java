package org.son.haruharu.domain.chat.storage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {

    // 삭제된(deleted_at) 메시지 제외는 BaseEntity @SoftDelete 전역 필터가 처리한다.
    List<ChatMessageEntity> findByChatRoomIdAndCreatedAtGreaterThanEqualOrderByIdDesc(
            Long chatRoomId, LocalDateTime joinedAt, Pageable pageable);

    List<ChatMessageEntity> findByChatRoomIdAndIdLessThanAndCreatedAtGreaterThanEqualOrderByIdDesc(
            Long chatRoomId, Long cursorId, LocalDateTime joinedAt, Pageable pageable);

    Optional<ChatMessageEntity> findFirstByChatRoomIdAndCreatedAtGreaterThanEqualOrderByIdDesc(
            Long chatRoomId, LocalDateTime joinedAt);

    long countByChatRoomIdAndIdGreaterThanAndSenderIdNotAndCreatedAtGreaterThanEqual(
            Long chatRoomId, Long lastReadMessageId, Long excludeSenderId, LocalDateTime joinedAt);

    @Query(value = """
            SELECT count(*)
            FROM chat_room_members membership
            JOIN chat_messages message
              ON message.chat_room_id = membership.chat_room_id
            WHERE membership.user_id = :userId
              AND membership.left_at IS NULL
              AND message.deleted_at IS NULL
              AND message.sender_id <> :userId
              AND message.id > coalesce(membership.last_read_message_id, 0)
              AND message.created_at >= membership.joined_at
            """, nativeQuery = true)
    long countUnreadByUserId(@Param("userId") Long userId);

    // 네이티브 DELETE — 전역 @SoftDelete 우회(데모 재시드 시 이전 대화가 누적되지 않게 물리 삭제).
    @Modifying
    @Query(value = "delete from chat_messages where chat_room_id = :chatRoomId", nativeQuery = true)
    void hardDeleteByChatRoomId(@Param("chatRoomId") Long chatRoomId);
}
