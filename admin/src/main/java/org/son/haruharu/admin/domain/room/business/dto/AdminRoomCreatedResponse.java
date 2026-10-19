package org.son.haruharu.admin.domain.room.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 방 생성 결과(#318). 운영진이 이 코드를 신청자에게 그대로 전달한다. */
public record AdminRoomCreatedResponse(
        @Schema(description = "생성된 방 id", example = "12") Long roomId,
        @Schema(description = "발급된 초대코드", example = "K7M2QX") String code,
        @Schema(description = "테스트방 여부", example = "false") boolean isTest
) {
}
