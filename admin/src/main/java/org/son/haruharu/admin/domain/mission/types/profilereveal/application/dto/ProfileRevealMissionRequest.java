package org.son.haruharu.admin.domain.mission.types.profilereveal.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.son.haruharu.admin.domain.mission.common.application.dto.MissionDetailsRequest;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.son.haruharu.domain.mission.types.profilereveal.implement.ProfileRevealMissionDefinition;

public record ProfileRevealMissionRequest(
        List<String> revealFields,
        @Schema(hidden = true) @JsonProperty("content") String retiredContent
) implements MissionDetailsRequest {
    @Override
    public ProfileRevealMissionDefinition toDefinition() {
        // 예전 계약의 미션 본문을 조용히 버리지 않는다. 채팅 문구는 안내가 가진다.
        if (retiredContent != null && !retiredContent.isBlank())
            throw new InvalidMissionException("details.content", "안내 문구는 announcement.content에 입력해 주세요");
        return ProfileRevealMissionDefinition.from(revealFields);
    }
}
