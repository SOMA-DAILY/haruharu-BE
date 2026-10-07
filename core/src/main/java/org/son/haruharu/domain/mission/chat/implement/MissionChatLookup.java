package org.son.haruharu.domain.mission.chat.implement;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatMissionLookup;
import org.son.haruharu.domain.chat.implement.ChatMissionReference;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.RoomMissionReader;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MissionChatLookup implements ChatMissionLookup {
    private final MissionAnnouncementReader announcements;
    private final RoomMissionReader missions;

    @Override
    public Map<Long, ChatMissionReference> findByAnnouncementIds(Collection<Long> ids) {
        var found = announcements.findByIds(ids);
        var definitions = missions.findByIds(found.stream().map(MissionAnnouncement::roomMissionId).distinct().toList())
                .stream().collect(Collectors.toMap(RoomMission::id, Function.identity()));
        return found.stream().filter(a -> definitions.containsKey(a.roomMissionId()))
                .collect(Collectors.toMap(MissionAnnouncement::id, a -> new ChatMissionReference(
                        a.roomMissionId(), a.id(), a.announcementType().name(),
                        definitions.get(a.roomMissionId()).type().name())));
    }
}
