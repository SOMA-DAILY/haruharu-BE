package org.son.haruharu.domain.profile.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.profile.business.command.UpdateRoomProfileCommand;

/**
 * 방 온보딩 프로필 저장 요청(#455). 칸별 메시지(error.fields)를 내려야 해서 Bean Validation 대신 도메인(RoomProfile)이 검증한다.
 */
public record RoomProfileUpdateRequest(
        @Schema(description = "출생연도(올해−35 ~ 올해−18)", example = "2003") Integer birthYear,
        @Schema(description = "MBTI(대소문자 무관, 16개 중 하나)", example = "ENFP") String mbti,
        @Schema(description = "학과(1~15자)", example = "전자공학과") String department,
        @Schema(description = "요즘 빠져 있는 것(1~40자)", example = "러닝") String intoNow
) {
    public UpdateRoomProfileCommand toCommand() {
        return new UpdateRoomProfileCommand(birthYear, mbti, department, intoNow);
    }
}
