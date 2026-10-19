package org.son.haruharu.domain.mission.common.implement;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MissionTypeRegistry {
    private final Map<MissionType, MissionTypeHandler> rules = new EnumMap<>(MissionType.class);

    public MissionTypeRegistry(List<MissionTypeHandler> implementations) {
        for (MissionTypeHandler implementation : implementations) {
            if (rules.putIfAbsent(implementation.type(), implementation) != null) {
                throw new IllegalStateException("Duplicate mission rules: " + implementation.type());
            }
        }
        for (MissionType type : MissionType.values()) {
            if (!rules.containsKey(type)) throw new IllegalStateException("Missing mission rules: " + type);
        }
    }

    public MissionTypeHandler get(MissionType type) {
        return rules.get(type);
    }
}
