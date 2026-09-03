package org.son.haruharu.domain.room.application.dto;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.son.haruharu.domain.room.implement.InviteCodeGenerator;

/** 초대코드 입장 요청(#312). */
public record JoinRoomRequest(
        @NotBlank @Pattern(regexp = InviteCodeGenerator.CODE_REGEX) String code
) {
    public JoinRoomRequest {
        if (code != null) {
            code = code.trim().toUpperCase(Locale.ROOT);
        }
    }
}
