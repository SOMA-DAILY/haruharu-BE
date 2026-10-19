package org.son.haruharu.admin.domain.room.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminRoomTypeResponse(
        @Schema(description = "방 id") Long roomId,
        @Schema(description = "테스트방 여부") boolean isTest
) {
}
