package org.son.haruharu.domain.coin.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.coin.business.dto.LedgerPageResponse;
import org.son.haruharu.domain.coin.business.dto.WalletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "코인", description = "코인 지갑 잔액·이동 내역 조회")
@RestController
@RequestMapping("/api/v1/coin")
// 코인 비활성(#309) — haruharu.coin.enabled=true 일 때만 빈 등록. 기본 false라 엔드포인트 부재(404). 코드 보존.
@ConditionalOnProperty(prefix = "haruharu.coin", name = "enabled", havingValue = "true")
public class CoinController {

    private final CoinService coinService;

    public CoinController(CoinService coinService) {
        this.coinService = coinService;
    }

    @Operation(summary = "코인 잔액 조회",
            description = "내 코인 지갑 잔액을 반환한다. 유료(PAID)·리워드(REWARD) 버킷을 분리해 제공하며, 지갑이 없으면 0 잔액.")
    @GetMapping("/wallet")
    public ApiResponse<WalletResponse> getWallet(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(coinService.getWallet(userId));
    }

    @Operation(summary = "코인 내역 조회",
            description = "코인 이동 내역(적립·소모·환불)을 최신순 커서 페이지네이션으로 반환한다.")
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
        return ApiResponse.ok(coinService.getLedger(userId, cursor, size));
    }
}
