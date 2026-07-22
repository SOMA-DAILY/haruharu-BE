package org.son.haruharu.domain.chat.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatRoomJpaRepository extends JpaRepository<ChatRoomEntity, Long> {
    boolean existsByMatchResultId(Long matchResultId);
    Optional<ChatRoomEntity> findByMatchResultId(Long matchResultId);

    // 네이티브 DELETE — 전역 @SoftDelete는 JPQL 벌크 delete까지 soft로 바꾸므로, UNIQUE(match_result_id)를
    // 점유한 행을 실제로 지우려면 네이티브 SQL이어야 한다(데모 재시연 시 재생성 가능하게).
    @Modifying
    @Query(value = "delete from chat_rooms where match_result_id = :matchResultId", nativeQuery = true)
    void hardDeleteByMatchResultId(@Param("matchResultId") Long matchResultId);
}
