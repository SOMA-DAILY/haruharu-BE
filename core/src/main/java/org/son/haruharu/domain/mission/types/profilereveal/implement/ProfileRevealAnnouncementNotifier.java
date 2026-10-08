package org.son.haruharu.domain.mission.types.profilereveal.implement;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncement;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementNotifier;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementReader;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementStatus;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType;
import org.son.haruharu.domain.mission.chat.implement.MissionNotification;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.RoomMissionReader;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.springframework.stereotype.Component;

/** 공개 미션의 첫 START 안내가 나갈 때, 더 이른 공개 미션이 아직 열지 않은 항목만 PROFILE_REVEAL 로 알린다. */
@Component
@RequiredArgsConstructor
public class ProfileRevealAnnouncementNotifier implements MissionAnnouncementNotifier {
    private final RoomMissionReader missions;
    private final MissionAnnouncementReader announcements;

    @Override
    public MissionType type() { return MissionType.PROFILE_REVEAL; }

    @Override
    public List<MissionNotification> afterAnnouncement(RoomMission mission, MissionAnnouncement item) {
        if (item.announcementType() != MissionAnnouncementType.START) return List.of();
        boolean earlierStart = announcements.findByMissionId(mission.id()).stream()
                .anyMatch(a -> a.announcementType() == MissionAnnouncementType.START
                        && a.status() != MissionAnnouncementStatus.CANCELED
                        && (a.runAt().isBefore(item.runAt()) || a.runAt().equals(item.runAt()) && a.id() < item.id()));
        if (earlierStart) return List.of();
        Set<MissionRevealField> already = missions.findByRoomIdAndType(mission.roomId(), MissionType.PROFILE_REVEAL).stream()
                .filter(m -> m.canceledAt() == null && (m.opensAt().isBefore(mission.opensAt())
                        || m.opensAt().equals(mission.opensAt()) && m.id() < mission.id()))
                .flatMap(m -> fieldsOf(m).stream()).collect(Collectors.toSet());
        var newlyRevealed = fieldsOf(mission).stream().filter(f -> !already.contains(f)).toList();
        return newlyRevealed.isEmpty() ? List.of() : List.of(new MissionNotification(NotificationType.PROFILE_REVEAL,
                MissionRevealField.pushTitleOf(newlyRevealed), MissionRevealField.pushBodyOf(newlyRevealed)));
    }

    static List<MissionRevealField> fieldsOf(RoomMission mission) {
        return mission.details() instanceof ProfileRevealMissionDefinition reveal ? reveal.revealFields() : List.of();
    }
}
