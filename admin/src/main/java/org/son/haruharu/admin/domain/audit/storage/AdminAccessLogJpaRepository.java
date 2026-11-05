package org.son.haruharu.admin.domain.audit.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminAccessLogJpaRepository
        extends JpaRepository<AdminAccessLogEntity, Long>, JpaSpecificationExecutor<AdminAccessLogEntity> {

    /** 관리자별 조회(VIEW*)·반출(EXPORT) 수. 결과 열: admin_id, views, exports. */
    @Query(value = "SELECT admin_id, "
            + "SUM(CASE WHEN action LIKE 'VIEW%' THEN 1 ELSE 0 END) AS views, "
            + "SUM(CASE WHEN action = 'EXPORT' THEN 1 ELSE 0 END) AS exports "
            + "FROM admin_access_logs "
            + "WHERE admin_id IS NOT NULL AND occurred_at >= :from AND occurred_at < :to "
            + "GROUP BY admin_id ORDER BY admin_id", nativeQuery = true)
    List<Object[]> countByAdmin(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 하루 조회 수가 minCount 이상인 (관리자, 날짜). 결과 열: admin_id, view_day, cnt. */
    @Query(value = "SELECT admin_id, CAST(occurred_at AS DATE) AS view_day, COUNT(*) AS cnt "
            + "FROM admin_access_logs "
            + "WHERE admin_id IS NOT NULL AND action LIKE 'VIEW%' AND occurred_at >= :from AND occurred_at < :to "
            + "GROUP BY admin_id, CAST(occurred_at AS DATE) "
            + "HAVING COUNT(*) >= :minCount "
            + "ORDER BY cnt DESC, admin_id", nativeQuery = true)
    List<Object[]> findHeavyViewDays(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                     @Param("minCount") long minCount);
}
