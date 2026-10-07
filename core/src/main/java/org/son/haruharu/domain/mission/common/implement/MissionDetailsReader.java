package org.son.haruharu.domain.mission.common.implement;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MissionDetailsReader {
    private final MissionTypeRegistry registry;

    public MissionDefinition get(RoomMission mission) { return findAll(List.of(mission)).get(mission.id()); }

    /** 목록 크기와 무관하게 유형당 한 번 조회한다. */
    public Map<Long, MissionDefinition> findAll(Collection<RoomMission> missions) {
        Map<Long, MissionDefinition> result = new HashMap<>();
        missions.stream().collect(Collectors.groupingBy(RoomMission::type)).forEach((type, items) ->
                result.putAll(registry.get(type).loadDefinitions(items.stream().map(RoomMission::id).toList())));
        for (RoomMission mission : missions) {
            if (!result.containsKey(mission.id()) || result.get(mission.id()).type() != mission.type())
                throw new IllegalStateException("Missing or mismatched mission definition: missionId=" + mission.id());
        }
        return result;
    }
}
