package org.son.haruharu.admin.domain.mission.implement;

import org.son.haruharu.domain.mission.implement.Mission;

import java.util.List;
import java.util.Optional;

/** 어드민 미션 카탈로그 포트(도메인 계약). storage가 JPA로 구현한다. Entity 비의존. */
public interface AdminMissionRepository {
    List<Mission> findAll();
    Optional<Mission> findById(Long id);
    boolean existsByDescription(String description);
    Mission save(Mission mission);
}
