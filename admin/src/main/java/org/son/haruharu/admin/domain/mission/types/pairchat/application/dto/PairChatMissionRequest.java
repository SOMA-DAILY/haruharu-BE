package org.son.haruharu.admin.domain.mission.types.pairchat.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import org.son.haruharu.admin.domain.mission.common.application.dto.MissionDetailsRequest;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.son.haruharu.domain.mission.types.pairchat.implement.PairChatMissionDefinition;

/** 1:1 대화(#540)의 details 는 빈 객체다. 대화 시간은 미션의 opens_at·closes_at, 안내 글은 announcement 가 가진다. */
public record PairChatMissionRequest(
        @Schema(hidden = true) @JsonProperty("content") Object otherTypeContent,
        @Schema(hidden = true) @JsonProperty("reveal_fields") Object otherTypeRevealFields
) implements MissionDetailsRequest {
    @Override
    public PairChatMissionDefinition toDefinition() {
        // 다른 유형의 필드를 조용히 버리지 않는다.
        if (otherTypeContent != null)
            throw new InvalidMissionException("details.content", "1:1 대화에는 내용 칸이 없어요. 안내 글은 announcement.content에 입력해 주세요");
        if (otherTypeRevealFields != null && !(otherTypeRevealFields instanceof Collection<?> fields && fields.isEmpty()))
            throw new InvalidMissionException("details.reveal_fields", "공개 항목은 PROFILE_REVEAL 미션으로 만들어 주세요");
        return new PairChatMissionDefinition();
    }
}
