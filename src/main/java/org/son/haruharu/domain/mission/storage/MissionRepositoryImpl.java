package org.son.haruharu.domain.mission.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MissionRepositoryImpl implements MissionRepository {

    private final MissionJpaRepository jpaRepository;

    @Override
    public List<Mission> findActive() {
        return jpaRepository.findByIsActiveTrue().stream().map(MissionEntity::toDomain).toList();
    }

    @Override
    public List<Mission> saveAll(List<Mission> missions) {
        return jpaRepository.saveAll(missions.stream().map(MissionEntity::from).toList()).stream()
                .map(MissionEntity::toDomain)
                .toList();
    }
}
