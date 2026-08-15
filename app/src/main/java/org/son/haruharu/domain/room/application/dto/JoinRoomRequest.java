package org.son.haruharu.domain.room.application.dto;

import jakarta.validation.constraints.NotBlank;

/** 초대코드 입장 요청(#312). */
public record JoinRoomRequest(
        @NotBlank String code
) {
}
