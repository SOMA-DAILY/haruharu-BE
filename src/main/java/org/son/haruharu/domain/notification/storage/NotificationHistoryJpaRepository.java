package org.son.haruharu.domain.notification.storage;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationHistoryJpaRepository extends JpaRepository<NotificationHistoryEntity, Long> {

    List<NotificationHistoryEntity> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    List<NotificationHistoryEntity> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long cursor, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Query("UPDATE NotificationHistoryEntity n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    void markAllRead(@Param("userId") Long userId);

    // 보관주기 물리 파기(retention purge). 전역 soft-delete는 HQL 벌크 DELETE도 soft로 치환하므로,
    // 실제 물리 삭제를 보장하려면 native DELETE로 우회한다.
    @Modifying
    @Query(value = "DELETE FROM notification_history WHERE created_at < :threshold", nativeQuery = true)
    int deleteByCreatedAtBefore(@Param("threshold") LocalDateTime threshold);
}
