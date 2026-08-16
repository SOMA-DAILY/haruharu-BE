package org.son.haruharu.admin.domain.room.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.admin.domain.room.business.command.AssignGenderCommand;

/**
 * 멤버 성별 부여 요청(#318). 허용 값 검증은 서비스가 하고 여기선 통과시킨다 —
 * 잘못된 값은 ADMIN_ROOM_INVALID_GENDER 로 일관되게 응답하기 위함(@Pattern 이면 메시지가 갈린다).
 */
public record AssignGenderRequest(
        @Schema(description = "성별(MALE·FEMALE)", example = "FEMALE") String gender
) {
    public AssignGenderCommand toCommand() {
        return new AssignGenderCommand(gender);
    }
}
