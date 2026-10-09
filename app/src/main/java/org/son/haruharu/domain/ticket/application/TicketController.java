package org.son.haruharu.domain.ticket.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.business.dto.LedgerPageResponse;
import org.son.haruharu.domain.ticket.business.dto.WalletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "티켓", description = "티켓 지갑 잔액·이동 내역 조회")
@RestController
@RequestMapping("/api/v1/ticket")
// haruharu.ticket.enabled=true 일 때만 빈 등록(#492). 기본 false라 엔드포인트 부재(404).
@ConditionalOnProperty(prefix = "haruharu.ticket", name = "enabled", havingValue = "true")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "티켓 잔액 조회",
            description = "내 티켓 잔액을 반환한다. 지갑이 없으면 0 잔액.")
    @GetMapping("/wallet")
    public ApiResponse<WalletResponse> getWallet(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(ticketService.getWallet(userId));
    }

    @Operation(summary = "티켓 내역 조회",
            description = "티켓 이동 내역(구매·사용·환불 회수·운영 지급)을 최신순 커서 페이지네이션으로 반환한다.")
    @GetMapping("/ledger")
    public ApiResponse<LedgerPageResponse> getLedger(
            Authentication authentication,
            // Long으로 받아 숫자가 아닌 커서를 Spring이 400(INVALID_TYPE)으로 거른다(#304).
            // String으로 받으면 Service의 파싱에서 NumberFormatException → catch-all 500이 된다.
            @Parameter(description = "이전 페이지 마지막 항목 id(커서). 첫 페이지는 생략", example = "1024")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "페이지 크기(기본 20)", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(ticketService.getLedger(userId, cursor, size));
    }
}
