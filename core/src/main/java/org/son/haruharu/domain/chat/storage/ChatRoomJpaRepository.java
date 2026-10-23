package org.son.haruharu.domain.chat.storage;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import org.son.haruharu.domain.chat.implement.ChatRoomType;

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
    // room_id 는 GROUP·PAIR 가 같이 쓴다(#540). 방의 GROUP 은 type 까지 걸어 찾는다.
    boolean existsByRoomIdAndType(Long roomId, ChatRoomType type);
    Optional<ChatRoomEntity> findByRoomIdAndType(Long roomId, ChatRoomType type);

    @Query("select c.id from ChatRoomEntity c where c.roomId = :roomId and c.type = :type")
    Optional<Long> findIdByRoomIdAndType(@Param("roomId") Long roomId, @Param("type") ChatRoomType type);

    // 같은 방 두 사람의 PAIR(#540). 생성은 원본 방 행 잠금 안에서만 하므로 쌍마다 1방이다.
    @Query(value = """
            select c.* from chat_rooms c
            join chat_room_members a on a.chat_room_id = c.id and a.user_id = :userA
            join chat_room_members b on b.chat_room_id = c.id and b.user_id = :userB
            where c.room_id = :roomId and c.type = 'PAIR' and c.deleted_at is null
            """, nativeQuery = true)
    Optional<ChatRoomEntity> findPairByRoomIdAndUsers(@Param("roomId") Long roomId,
                                                      @Param("userA") Long userA, @Param("userB") Long userB);

    @Query("select c from ChatRoomEntity c where c.roomId = :roomId and c.type = :type and c.closeAt is null order by c.id")
    List<ChatRoomEntity> findOpenByRoomIdAndType(@Param("roomId") Long roomId, @Param("type") ChatRoomType type);

    // 전송과 같은 행 잠금으로 직렬화하며 최초 종료 시각은 보존한다.
    // 원본 방 변경을 먼저 flush하고, bulk 갱신 전의 close_at이 1차 캐시에 남지 않게 한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update chat_rooms set close_at = :closeAt, updated_at = :closeAt
            where room_id = :roomId and type = 'GROUP' and close_at is null and deleted_at is null
            """, nativeQuery = true)
    int closeGroupByRoomId(@Param("roomId") Long roomId, @Param("closeAt") LocalDateTime closeAt);

    // 방 종료 때 그 방의 대화 시간 1:1(PAIR) 채팅도 닫는다(#540). GROUP 종료와 같은 규칙(최초 종료 시각 보존).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update chat_rooms set close_at = :closeAt, updated_at = :closeAt
            where room_id = :roomId and type = 'PAIR' and close_at is null and deleted_at is null
            """, nativeQuery = true)
    int closePairsByRoomId(@Param("roomId") Long roomId, @Param("closeAt") LocalDateTime closeAt);
}
