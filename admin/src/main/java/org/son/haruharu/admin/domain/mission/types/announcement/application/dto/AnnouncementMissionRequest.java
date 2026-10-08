package org.son.haruharu.admin.domain.mission.types.announcement.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import org.son.haruharu.admin.domain.mission.common.application.dto.MissionDetailsRequest;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionDefinition;

public record AnnouncementMissionRequest(
        String content,
        @Schema(hidden = true) @JsonProperty("reveal_fields") Object retiredRevealFields
) implements MissionDetailsRequest {
    @Override
    public AnnouncementMissionDefinition toDefinition() {
        // 예전 계약의 공개 항목을 조용히 버리지 않는다.
        if (retiredRevealFields != null && !(retiredRevealFields instanceof Collection<?> fields && fields.isEmpty()))
            throw new InvalidMissionException("details.reveal_fields", "공개 항목은 PROFILE_REVEAL 미션으로 만들어 주세요");
        return new AnnouncementMissionDefinition(content);
    }
}
