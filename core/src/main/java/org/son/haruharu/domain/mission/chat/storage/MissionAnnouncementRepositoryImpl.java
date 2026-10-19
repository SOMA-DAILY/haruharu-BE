package org.son.haruharu.domain.mission.chat.storage;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncement;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementRepository;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import static org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementStatus.*;

@Repository
@RequiredArgsConstructor
public class MissionAnnouncementRepositoryImpl implements MissionAnnouncementRepository {
    private final MissionAnnouncementJpaRepository repository;

    @Override
    public List<MissionAnnouncement> findPage(Long missionId, Long after, int limit) {
        return repository.findByRoomMissionIdAndIdGreaterThanOrderByIdAsc(missionId, after == null ? 0L : after,
                org.springframework.data.domain.PageRequest.of(0, limit)).stream().map(MissionAnnouncementEntity::toDomain).toList();
    }

    @Override
    public MissionAnnouncement save(MissionAnnouncement announcement) {
        return repository.save(MissionAnnouncementEntity.from(announcement)).toDomain();
    }

    @Override
    public Optional<MissionAnnouncement> findById(Long id) {
        return repository.findById(id).map(MissionAnnouncementEntity::toDomain);
    }

    @Override
    public Optional<MissionAnnouncement> findByIdForUpdate(Long id) {
        return repository.findByIdForUpdate(id).map(MissionAnnouncementEntity::toDomain);
    }

    @Override
    public List<MissionAnnouncement> findByRoomId(Long roomId) {
        return repository.findByRoomIdOrderByRunAtAscIdAsc(roomId).stream().map(MissionAnnouncementEntity::toDomain).toList();
    }

    @Override
    public List<MissionAnnouncement> findByMissionId(Long missionId) {
        return repository.findByRoomMissionIdOrderByRunAtAscIdAsc(missionId).stream().map(MissionAnnouncementEntity::toDomain).toList();
    }

    @Override
    public List<MissionAnnouncement> findByIds(Collection<Long> ids) {
        return repository.findAllById(ids).stream().map(MissionAnnouncementEntity::toDomain).toList();
    }

    @Override
    public List<MissionAnnouncement> findDue(LocalDateTime now, int limit) {
        return repository.findDue(SCHEDULED, now, PageRequest.of(0, limit)).stream().map(MissionAnnouncementEntity::toDomain).toList();
    }

    @Override
    public List<MissionAnnouncement> findSendingSince(LocalDateTime threshold, int limit) {
        return repository.findSendingSince(SENDING, threshold, PageRequest.of(0, limit)).stream().map(MissionAnnouncementEntity::toDomain).toList();
    }

    @Override
    public int claim(Long id, LocalDateTime now) {
        return repository.claim(id, SCHEDULED, SENDING, now);
    }

    @Override
    public int markSent(Long id, Long messageId, LocalDateTime now) {
        return repository.markSent(id, messageId, now, SENDING, SENT);
    }

    @Override
    public int markFailed(Long id, MissionAnnouncementStatus from, String reason, LocalDateTime now) {
        return repository.markFailed(id, from, FAILED, reason, now);
    }

    @Override
    public int markFailedIfUnchanged(MissionAnnouncement snapshot, String reason, LocalDateTime now) {
        return repository.markFailedIfUnchanged(snapshot.id(), snapshot.status(), FAILED,
                snapshot.runAt(), snapshot.updatedAt(), reason, now);
    }

    @Override
    public int updateIfScheduled(Long id, LocalDateTime runAt, String content, boolean push, String title,
                                 String body, String actor, LocalDateTime now) {
        return repository.updateIfScheduled(id, runAt, content, push, title, body, actor, now, SCHEDULED);
    }

    @Override
    public int cancelIfScheduled(Long id, String actor, LocalDateTime now) {
        return repository.cancelIfScheduled(id, actor, now, SCHEDULED, CANCELED);
    }

    @Override
    public int cancelScheduledForMission(Long missionId, String actor, LocalDateTime now) {
        return repository.cancelScheduledForMission(missionId, actor, now, SCHEDULED, CANCELED);
    }
}
