package org.son.haruharu.domain.mission.chat.implement;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MissionAnnouncementWriter {
    private final MissionAnnouncementRepository repository;

    public MissionAnnouncement save(MissionAnnouncement announcement) {
        return repository.save(announcement);
    }

    public boolean claim(Long id, LocalDateTime now) {
        return repository.claim(id, now) == 1;
    }

    public boolean markSent(Long id, Long messageId, LocalDateTime now) {
        return repository.markSent(id, messageId, now) == 1;
    }

    public boolean markFailed(Long id, MissionAnnouncementStatus from, String reason, LocalDateTime now) {
        return repository.markFailed(id, from, reason, now) == 1;
    }

    public boolean markFailedIfUnchanged(MissionAnnouncement snapshot, String reason, LocalDateTime now) {
        return repository.markFailedIfUnchanged(snapshot, reason, now) == 1;
    }

    public boolean updateIfScheduled(Long id, LocalDateTime runAt, String content, boolean push,
                                     String title, String body, String actor, LocalDateTime now) {
        return repository.updateIfScheduled(id, runAt, content, push, title, body, actor, now) == 1;
    }

    public boolean cancelIfScheduled(Long id, String actor, LocalDateTime now) {
        return repository.cancelIfScheduled(id, actor, now) == 1;
    }

    public void cancelScheduledForMission(Long missionId, String actor, LocalDateTime now) {
        repository.cancelScheduledForMission(missionId, actor, now);
    }
}
