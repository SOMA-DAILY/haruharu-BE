package org.son.haruharu.domain.agreement.application;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.agreement.application.dto.SaveAgreementsRequest;
import org.son.haruharu.domain.agreement.business.AgreementService;
import org.son.haruharu.domain.agreement.business.dto.MarketingConsentResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/agreements")
public class AgreementController {

    private final AgreementService agreementService;

    public AgreementController(AgreementService agreementService) {
        this.agreementService = agreementService;
    }

    @PostMapping
    public ApiResponse<Void> saveAgreements(Authentication authentication, @RequestBody SaveAgreementsRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        agreementService.saveAgreements(userId, request.toCommand());
        return ApiResponse.ok(null);
    }

    @GetMapping("/marketing")
    public ApiResponse<MarketingConsentResponse> getMarketingConsent(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(agreementService.getMarketingConsent(userId));
    }
}
