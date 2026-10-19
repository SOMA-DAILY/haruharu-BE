package org.son.haruharu.domain.cohort.storage;

import jakarta.persistence.LockModeType;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CohortJpaRepository extends JpaRepository<CohortEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CohortEntity c where c.id = :id")
    Optional<CohortEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from CohortEntity c where c.id = :id")
    Optional<CohortEntity> findByIdForShare(@Param("id") Long id);

    @Query("select c.trackId from CohortEntity c where c.id = :id")
    Optional<Long> findTrackIdById(@Param("id") Long id);

    @Query("""
            select c from CohortEntity c
            where (:trackId is null or c.trackId = :trackId)
              and (:status is null or c.status = :status)
            order by c.trackId asc, c.sequence desc
            """)
    List<CohortEntity> findAllFiltered(@Param("trackId") Long trackId, @Param("status") CohortStatus status);

    List<CohortEntity> findAllByStatus(CohortStatus status);

    boolean existsByTrackIdAndSequence(Long trackId, int sequence);

    boolean existsByTrackIdAndStatus(Long trackId, CohortStatus status);
}
