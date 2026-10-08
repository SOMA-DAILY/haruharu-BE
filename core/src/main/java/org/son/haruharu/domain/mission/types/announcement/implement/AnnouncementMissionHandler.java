package org.son.haruharu.domain.mission.types.announcement.implement;

import java.util.Map;
import org.son.haruharu.domain.mission.common.implement.MissionDefinition;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.MissionTypeHandler;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.springframework.stereotype.Component;

@Component
public class AnnouncementMissionHandler implements MissionTypeHandler {
    static final String CONTENT = "content";

    @Override
    public MissionType type() { return MissionType.ANNOUNCEMENT; }
    @Override
    public void validate(MissionDefinition definition) { announcement(definition).validate(); }
    @Override
    public boolean requiresResponse() { return false; }

    @Override
    public Map<String, Object> toStored(MissionDefinition definition) {
        String content = announcement(definition).content();
        return content == null ? Map.of() : Map.of(CONTENT, content);
    }

    @Override
    public MissionDefinition fromStored(Map<String, Object> stored) {
        return new AnnouncementMissionDefinition(stored.get(CONTENT) instanceof String content ? content : null);
    }

    private AnnouncementMissionDefinition announcement(MissionDefinition definition) {
        if (!(definition instanceof AnnouncementMissionDefinition announcement))
            throw new InvalidMissionException("details", "안내형 내용을 입력해 주세요");
        return announcement;
    }
}
