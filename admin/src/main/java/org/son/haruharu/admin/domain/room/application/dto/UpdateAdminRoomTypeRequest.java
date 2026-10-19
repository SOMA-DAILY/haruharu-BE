package org.son.haruharu.admin.domain.room.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.son.haruharu.admin.domain.room.business.command.UpdateAdminRoomTypeCommand;

public record UpdateAdminRoomTypeRequest(
        @Schema(description = "테스트방 여부", example = "true") @NotNull Boolean isTest
) {
    public UpdateAdminRoomTypeCommand toCommand() {
        return new UpdateAdminRoomTypeCommand(isTest);
    }
}
