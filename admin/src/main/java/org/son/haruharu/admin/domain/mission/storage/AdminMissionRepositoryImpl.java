package org.son.haruharu.admin.domain.mission.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.admin.domain.mission.implement.AdminMissionRepository;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.storage.MissionEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 어드민 미션 카탈로그 포트의 JPA 어댑터. Entity↔Domain 변환을 전담한다. */
@Repository
@RequiredArgsConstructor
public class AdminMissionRepositoryImpl implements AdminMissionRepository {

    private final AdminMissionJpaRepository jpaRepository;

    @Override
    public List<Mission> findAll() {
        return jpaRepository.findAll().stream().map(MissionEntity::toDomain).toList();
    }

    @Override
    public Optional<Mission> findById(Long id) {
        return jpaRepository.findById(id).map(MissionEntity::toDomain);
    }

    @Override
    public boolean existsByDescription(String description) {
        return jpaRepository.existsByDescription(description);
    }

    @Override
    public Mission save(Mission mission) {
        return jpaRepository.save(MissionEntity.from(mission)).toDomain();
    }
}
