package org.son.haruharu.domain.agreement.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "약관", description = "서비스 이용약관·마케팅 수신 등 약관 동의 저장 및 마케팅 동의 상태 조회")
@RestController
@RequestMapping("/api/v1/users/me/agreements")
public class AgreementController {

    private final AgreementService agreementService;

    public AgreementController(AgreementService agreementService) {
        this.agreementService = agreementService;
    }

    @Operation(summary = "약관 동의 저장",
            description = "약관 항목별 동의 내역(type·version·agreed)을 저장한다. "
                    + "필수 약관에 모두 동의하지 않으면 400(REQUIRED_AGREEMENT_MISSING).")
    @PostMapping
    public ApiResponse<Void> saveAgreements(Authentication authentication, @RequestBody SaveAgreementsRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        agreementService.saveAgreements(userId, request.toCommand());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "마케팅 동의 상태 조회",
            description = "최신 마케팅 수신 동의 여부(agreed)·버전(version)·동의 시각(agreed_at)을 반환한다. 이력이 없으면 미동의로 응답한다.")
    @GetMapping("/marketing")
    public ApiResponse<MarketingConsentResponse> getMarketingConsent(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(agreementService.getMarketingConsent(userId));
    }
}
