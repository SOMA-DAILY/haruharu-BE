package org.son.haruharu.domain.cohort.implement;

import java.util.List;
import java.util.Optional;

public interface RecruitTrackRepository {

    Optional<RecruitTrack> findById(Long id);

    /** 트랙 행 잠금. 같은 트랙의 기수 생성·공개·수정을 한 줄로 세운다. */
    Optional<RecruitTrack> findByIdForUpdate(Long id);

    /** 표시 순서, id 순. */
    List<RecruitTrack> findAll();

    List<RecruitTrack> findAllByIdIn(List<Long> ids);

    boolean existsByKey(String key);

    RecruitTrack save(RecruitTrack track);
}
