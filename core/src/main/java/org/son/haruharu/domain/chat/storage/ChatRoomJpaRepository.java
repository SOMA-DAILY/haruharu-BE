package org.son.haruharu.domain.chat.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatRoomJpaRepository extends JpaRepository<ChatRoomEntity, Long> {
    // 종료 요청 재시도도 같은 행으로 직렬화한다. 엔티티 조회와 달리 삭제된 행을 포함한다.
    @Query(value = "select id from chat_rooms where id = :id for update", nativeQuery = true)
    Optional<Long> lockByIdIncludingDeleted(@Param("id") Long id);

    @Query(value = "select count(*) > 0 from chat_rooms where match_result_id = :matchResultId", nativeQuery = true)
    boolean existsIncludingDeletedByMatchResultId(@Param("matchResultId") Long matchResultId);

    boolean existsByMatchResultId(Long matchResultId);
    Optional<ChatRoomEntity> findByMatchResultId(Long matchResultId);
    boolean existsByRoomId(Long roomId);
    Optional<ChatRoomEntity> findByRoomId(Long roomId);
}
