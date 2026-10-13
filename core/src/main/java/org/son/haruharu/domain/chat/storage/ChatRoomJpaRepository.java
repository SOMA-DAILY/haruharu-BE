package org.son.haruharu.domain.chat.storage;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatRoomJpaRepository extends JpaRepository<ChatRoomEntity, Long> {
    // 종료 요청 재시도도 같은 행으로 직렬화한다. 엔티티 조회와 달리 삭제된 행을 포함한다.
    @Query(value = "select id from chat_rooms where id = :id for update", nativeQuery = true)
    Optional<Long> lockByIdIncludingDeleted(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @Query("select c from ChatRoomEntity c where c.id = :id")
    Optional<ChatRoomEntity> findByIdForShare(@Param("id") Long id);

    @Query(value = "select count(*) > 0 from chat_rooms where match_result_id = :matchResultId", nativeQuery = true)
    boolean existsIncludingDeletedByMatchResultId(@Param("matchResultId") Long matchResultId);

    boolean existsByMatchResultId(Long matchResultId);
    Optional<ChatRoomEntity> findByMatchResultId(Long matchResultId);
    boolean existsByRoomId(Long roomId);
    Optional<ChatRoomEntity> findByRoomId(Long roomId);

    @Query("select c.id from ChatRoomEntity c where c.roomId = :roomId")
    Optional<Long> findIdByRoomId(@Param("roomId") Long roomId);

    // 전송과 같은 행 잠금으로 직렬화하며 최초 종료 시각은 보존한다.
    // 원본 방 변경을 먼저 flush하고, bulk 갱신 전의 close_at이 1차 캐시에 남지 않게 한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update chat_rooms set close_at = :closeAt, updated_at = :closeAt
            where room_id = :roomId and type = 'GROUP' and close_at is null and deleted_at is null
            """, nativeQuery = true)
    int closeGroupByRoomId(@Param("roomId") Long roomId, @Param("closeAt") LocalDateTime closeAt);
}
