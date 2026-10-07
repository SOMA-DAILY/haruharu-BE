package org.son.haruharu.domain.mission.types.announcement.storage;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMission;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionRepository;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementRevealPlan;
import org.son.haruharu.domain.mission.types.announcement.implement.MissionRevealField;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AnnouncementMissionRepositoryImpl implements AnnouncementMissionRepository {
    private final AnnouncementMissionJpaRepository repository;

    @Override
    public AnnouncementMission save(AnnouncementMission mission) {
        return repository.save(AnnouncementMissionEntity.from(mission)).toDomain();
    }
    @Override
    public Optional<AnnouncementMission> findByMissionId(Long id) {
        return repository.findById(id).map(AnnouncementMissionEntity::toDomain);
    }
    @Override
    public List<AnnouncementMission> findByMissionIds(Collection<Long> ids) {
        return repository.findAllById(ids).stream().map(AnnouncementMissionEntity::toDomain).toList();
    }
    @Override
    public List<AnnouncementRevealPlan> findRevealPlans(Long roomId) {
        return repository.findRevealPlans(roomId).stream().map(row -> new AnnouncementRevealPlan(row.getMissionId(),
                row.getOpensAt(), row.getCanceledAt(), MissionRevealField.parseCsv(row.getRevealFields()))).toList();
    }
}
