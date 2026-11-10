package org.son.haruharu.domain.chat.storage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {

    Optional<ChatMessageEntity> findByChatRoomIdAndSenderIdAndClientRequestId(Long chatRoomId, Long senderId, String clientRequestId);

    // 삭제된(deleted_at) 메시지 제외는 BaseEntity @SoftDelete 전역 필터가 처리한다.
    List<ChatMessageEntity> findByChatRoomIdOrderByIdDesc(Long chatRoomId, Pageable pageable);

    List<ChatMessageEntity> findByChatRoomIdAndIdLessThanOrderByIdDesc(
            Long chatRoomId, Long cursorId, Pageable pageable);

    Optional<ChatMessageEntity> findFirstByChatRoomIdOrderByIdDesc(Long chatRoomId);

    /** 답장 추천(#558): 기준 시각 이전 메시지 전체(시간순). 퇴장 멤버의 글은 @SQLRestriction 으로 빠진다. */
    List<ChatMessageEntity> findByChatRoomIdAndCreatedAtBeforeOrderByIdAsc(Long chatRoomId, java.time.LocalDateTime before);

    // 차단한 발신자 제외 미리보기(#408). excludeSenderIds가 비어 있으면 위 메서드를 쓴다(NOT IN 빈 목록 방지).
    @Query("select m from ChatMessageEntity m where m.chatRoomId = :chatRoomId "
            + "and (m.senderId is null or m.senderId not in :excludeSenderIds) order by m.id desc")
    List<ChatMessageEntity> findLastExcludingSenders(@Param("chatRoomId") Long chatRoomId,
            @Param("excludeSenderIds") Collection<Long> excludeSenderIds, Pageable pageable);

    @Query("select count(m) from ChatMessageEntity m where m.chatRoomId = :chatRoomId and m.id > :cursor "
            + "and (m.senderId is null or m.senderId not in :excludeSenderIds)")
    long countUnreadExcludingSenders(@Param("chatRoomId") Long chatRoomId, @Param("cursor") Long cursor,
            @Param("excludeSenderIds") Collection<Long> excludeSenderIds);

    long countByChatRoomIdAndIdGreaterThan(Long chatRoomId, Long cursor);

    @Modifying(clearAutomatically = true)
    @Query("update ChatMessageEntity m set m.adminDeletedAt = :now where m.id = :id and m.adminDeletedAt is null")
    int markAdminDeleted(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);

    @Query("select m from ChatMessageEntity m where m.adminDeletedAt is not null and m.deleteBroadcastAt is null "
            + "order by m.id")
    List<ChatMessageEntity> findPendingDeleteBroadcasts(Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("update ChatMessageEntity m set m.deleteBroadcastAt = :now where m.id = :id and m.deleteBroadcastAt is null")
    int markDeleteBroadcast(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);
}
