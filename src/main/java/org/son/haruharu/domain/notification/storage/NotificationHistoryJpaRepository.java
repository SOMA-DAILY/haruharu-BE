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

    @Modifying
    @Query("DELETE FROM NotificationHistoryEntity n WHERE n.createdAt < :threshold")
    int deleteByCreatedAtBefore(@Param("threshold") LocalDateTime threshold);
}
