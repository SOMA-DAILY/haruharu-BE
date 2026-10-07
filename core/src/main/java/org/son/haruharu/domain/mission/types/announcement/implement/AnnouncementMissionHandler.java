package org.son.haruharu.domain.mission.types.announcement.implement;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncement;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementReader;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementStatus;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType;
import org.son.haruharu.domain.mission.chat.implement.MissionNotification;
import org.son.haruharu.domain.mission.common.implement.MissionDefinition;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.MissionTypeHandler;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnnouncementMissionHandler implements MissionTypeHandler {
    private final AnnouncementMissionReader reader;
    private final AnnouncementMissionWriter writer;
    private final MissionAnnouncementReader announcements;

    @Override
    public MissionType type() { return MissionType.ANNOUNCEMENT; }
    @Override
    public void validate(MissionDefinition definition) { announcement(definition).validate(); }
    @Override
    public boolean requiresResponse() { return false; }

    @Override
    public void saveDefinition(Long missionId, MissionDefinition definition) {
        var details = announcement(definition);
        var mission = reader.findByMissionId(missionId).map(existing -> existing.withDefinition(details))
                .orElseGet(() -> AnnouncementMission.create(missionId, details));
        writer.save(mission);
    }

    @Override
    public Map<Long, MissionDefinition> loadDefinitions(Collection<Long> missionIds) {
        return reader.findByMissionIds(missionIds).stream()
                .collect(Collectors.toMap(AnnouncementMission::roomMissionId, AnnouncementMission::definition));
    }

    @Override
    public boolean allowsEmptyAnnouncement(MissionAnnouncementType purpose, MissionDefinition definition) {
        return purpose == MissionAnnouncementType.START && !announcement(definition).revealFields().isEmpty();
    }

    @Override
    public List<MissionNotification> notificationsAfterAnnouncement(RoomMission mission, MissionAnnouncement item) {
        var details = reader.getByMissionId(mission.id()).definition();
        if (item.announcementType() != MissionAnnouncementType.START || details.revealFields().isEmpty()) return List.of();
        boolean earlierStart = announcements.findByMissionId(mission.id()).stream()
                .anyMatch(a -> a.announcementType() == MissionAnnouncementType.START
                        && a.status() != MissionAnnouncementStatus.CANCELED
                        && (a.runAt().isBefore(item.runAt()) || a.runAt().equals(item.runAt()) && a.id() < item.id()));
        if (earlierStart) return List.of();
        Set<MissionRevealField> already = reader.findRevealPlans(mission.roomId()).stream()
                .filter(p -> p.canceledAt() == null && (p.opensAt().isBefore(mission.opensAt())
                        || p.opensAt().equals(mission.opensAt()) && p.missionId() < mission.id()))
                .flatMap(p -> p.revealFields().stream()).collect(Collectors.toSet());
        var newlyRevealed = details.revealFields().stream().filter(f -> !already.contains(f)).toList();
        return newlyRevealed.isEmpty() ? List.of() : List.of(new MissionNotification(NotificationType.PROFILE_REVEAL,
                MissionRevealField.pushTitleOf(newlyRevealed), MissionRevealField.pushBodyOf(newlyRevealed)));
    }

    private AnnouncementMissionDefinition announcement(MissionDefinition definition) {
        if (!(definition instanceof AnnouncementMissionDefinition announcement))
            throw new InvalidMissionException("details", "안내형 내용을 입력해 주세요");
        return announcement;
    }
}
