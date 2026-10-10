package org.son.haruharu.domain.mission.types.recording.implement;

import java.util.Map;
import org.son.haruharu.domain.mission.common.implement.MissionDefinition;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.MissionTypeHandler;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.springframework.stereotype.Component;

@Component
public class RecordingMissionHandler implements MissionTypeHandler {
    static final String CONTENT = "content";

    @Override
    public MissionType type() { return MissionType.RECORDING; }
    @Override
    public void validate(MissionDefinition definition) { recording(definition).validate(); }
    /** 제출 기록을 서버가 받지 않는다. 촬영한 영상은 기존 영상 업로드를 그대로 쓴다. */
    @Override
    public boolean requiresResponse() { return false; }

    @Override
    public Map<String, Object> toStored(MissionDefinition definition) {
        String content = recording(definition).content();
        return content == null ? Map.of() : Map.of(CONTENT, content);
    }

    @Override
    public MissionDefinition fromStored(Map<String, Object> stored) {
        return new RecordingMissionDefinition(stored.get(CONTENT) instanceof String content ? content : null);
    }

    private RecordingMissionDefinition recording(MissionDefinition definition) {
        if (!(definition instanceof RecordingMissionDefinition recording))
            throw new InvalidMissionException("details", "촬영 미션 내용을 입력해 주세요");
        return recording;
    }
}
