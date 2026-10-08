package org.son.haruharu.domain.mission.chat.implement;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.chat.implement.ChatMissionImage;
import org.son.haruharu.domain.chat.implement.ChatMissionLookup;
import org.son.haruharu.domain.chat.implement.ChatMissionReference;
import org.son.haruharu.domain.mission.common.implement.MissionImage;
import org.son.haruharu.domain.mission.common.implement.MissionImageStore;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.RoomMissionReader;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MissionChatLookup implements ChatMissionLookup {
    private final MissionAnnouncementReader announcements;
    private final RoomMissionReader missions;
    private final MissionImageStore images;

    @Override
    public Map<Long, ChatMissionReference> findByAnnouncementIds(Collection<Long> ids) {
        var found = announcements.findByIds(ids);
        var definitions = missions.findByIds(found.stream().map(MissionAnnouncement::roomMissionId).distinct().toList())
                .stream().collect(Collectors.toMap(RoomMission::id, Function.identity()));
        return found.stream().filter(a -> definitions.containsKey(a.roomMissionId()))
                .collect(Collectors.toMap(MissionAnnouncement::id, a -> new ChatMissionReference(
                        a.roomMissionId(), a.id(), a.announcementType().name(),
                        definitions.get(a.roomMissionId()).type().name(), image(definitions.get(a.roomMissionId())))));
    }

    /** 실행기의 실시간 방송도 같은 모양을 쓴다. */
    public ChatMissionImage image(RoomMission mission) {
        MissionImage image = mission.image();
        if (image == null) return null;
        try {
            return new ChatMissionImage(image.key(), images.signedUrl(image), image.width(), image.height());
        } catch (RuntimeException e) {
            // 주소 발급 실패로 안내 발송·기록 조회를 막지 않는다. 사진만 빠지고 미션 조회에서 다시 받을 수 있다.
            log.warn("미션 사진 조회 주소 발급 실패: missionId={}", mission.id(), e);
            return null;
        }
    }
}
