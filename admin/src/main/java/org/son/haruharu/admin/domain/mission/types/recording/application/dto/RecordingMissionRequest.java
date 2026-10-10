package org.son.haruharu.admin.domain.mission.types.recording.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import org.son.haruharu.admin.domain.mission.common.application.dto.MissionDetailsRequest;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.son.haruharu.domain.mission.types.recording.implement.RecordingMissionDefinition;

public record RecordingMissionRequest(
        String content,
        @Schema(hidden = true) @JsonProperty("reveal_fields") Object otherTypeRevealFields
) implements MissionDetailsRequest {
    @Override
    public RecordingMissionDefinition toDefinition() {
        // 다른 유형의 필드를 조용히 버리지 않는다.
        if (otherTypeRevealFields != null && !(otherTypeRevealFields instanceof Collection<?> fields && fields.isEmpty()))
            throw new InvalidMissionException("details.reveal_fields", "공개 항목은 PROFILE_REVEAL 미션으로 만들어 주세요");
        return new RecordingMissionDefinition(content);
    }
}
