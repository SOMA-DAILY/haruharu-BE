package org.son.haruharu.domain.cohort.implement;

import java.util.List;
import java.util.Optional;

public interface CohortRepository {

    Optional<Cohort> findById(Long id);

    Optional<Cohort> findByIdForUpdate(Long id);

    /** 엔티티를 영속성 컨텍스트에 올리지 않고 트랙 id 만 읽는다. 이후 잠금 조회가 최신 행을 보게 한다. */
    Optional<Long> findTrackIdById(Long id);

    /** 조건은 null 이면 거르지 않는다. 트랙 id, 기수 번호 내림차순. */
    List<Cohort> findAll(Long trackId, CohortStatus status);

    List<Cohort> findAllByStatus(CohortStatus status);

    boolean existsByTrackIdAndSequence(Long trackId, int sequence);

    boolean existsByTrackIdAndStatus(Long trackId, CohortStatus status);

    Cohort save(Cohort cohort);
}
