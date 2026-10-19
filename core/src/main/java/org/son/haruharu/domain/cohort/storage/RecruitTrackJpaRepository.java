package org.son.haruharu.domain.cohort.storage;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecruitTrackJpaRepository extends JpaRepository<RecruitTrackEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RecruitTrackEntity t where t.id = :id")
    Optional<RecruitTrackEntity> findByIdForUpdate(@Param("id") Long id);

    List<RecruitTrackEntity> findAllByOrderByDisplayOrderAscIdAsc();

    List<RecruitTrackEntity> findAllByIdIn(List<Long> ids);

    boolean existsByKey(String key);
}
