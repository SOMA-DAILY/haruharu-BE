package org.son.haruharu.domain.coin.application;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.coin.business.dto.LedgerPageResponse;
import org.son.haruharu.domain.coin.business.dto.WalletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coin")
public class CoinController {

    private final CoinService coinService;

    public CoinController(CoinService coinService) {
        this.coinService = coinService;
    }

    @GetMapping("/wallet")
    public ApiResponse<WalletResponse> getWallet(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(coinService.getWallet(userId));
    }

    @GetMapping("/ledger")
    public ApiResponse<LedgerPageResponse> getLedger(
            Authentication authentication,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(coinService.getLedger(userId, cursor, size));
    }
}
