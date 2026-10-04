package org.son.haruharu.domain.mission.storage;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.son.haruharu.domain.mission.implement.*;
@Repository @RequiredArgsConstructor
public class MissionImageRepositoryImpl implements MissionImageRepository {
    private final MissionImageJpaRepository repository;
    public MissionImage save(MissionImage image) { return repository.save(MissionImageEntity.from(image)).toDomain(); }
    public Optional<MissionImage> findById(Long id) { return repository.findById(id).map(MissionImageEntity::toDomain); }
    public java.util.List<MissionImage> findAllById(java.util.Collection<Long> ids) { return repository.findAllById(ids).stream().map(MissionImageEntity::toDomain).toList(); }
}
