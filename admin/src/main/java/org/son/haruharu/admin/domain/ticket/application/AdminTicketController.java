package org.son.haruharu.admin.domain.ticket.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.admin.domain.ticket.application.dto.GrantTicketRequest;
import org.son.haruharu.admin.domain.ticket.business.AdminTicketService;
import org.son.haruharu.admin.domain.ticket.business.dto.AdminTicketGrantResponse;
import org.son.haruharu.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Ticket", description = "운영진 티켓 지급(SUPER 전용)")
@RestController
@RequestMapping("/api/v1/admin/users/{userId}/tickets")
public class AdminTicketController {

    private final AdminTicketService adminTicketService;

    public AdminTicketController(AdminTicketService adminTicketService) {
        this.adminTicketService = adminTicketService;
    }

    @Operation(summary = "티켓 지급", description = "유저에게 티켓을 지급한다. 지급한 어드민과 사유 메모가 원장에 남는다. "
            + "없는 유저·탈퇴 유저는 404. SUPER 전용.")
    @PostMapping
    public ApiResponse<AdminTicketGrantResponse> grant(
            @AuthenticationPrincipal Long adminId,
            @Parameter(description = "지급받을 유저 id", example = "42") @PathVariable Long userId,
            @RequestBody @Valid GrantTicketRequest request) {
        return ApiResponse.ok(adminTicketService.grant(request.toCommand(adminId, userId)));
    }
}
