package org.son.haruharu.admin.domain.ticket.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.son.haruharu.admin.domain.ticket.business.command.GrantTicketCommand;

public record GrantTicketRequest(
        // 상한은 입력 실수(자릿수 오타)로 대량 지급되는 사고를 막기 위한 값이다.
        @Schema(description = "지급 티켓 수량(1~100)", example = "1")
        @Positive @Max(100) long amount,
        @Schema(description = "지급 사유(내부 기록, 유저에게 노출되지 않음)", example = "결제 오류 보상")
        @NotBlank @Size(max = 200) String memo
) {
    public GrantTicketCommand toCommand(Long adminId, Long userId) {
        return new GrantTicketCommand(adminId, userId, amount, memo.strip());
    }
}
