package org.son.haruharu.domain.payment.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.payment.application.dto.RevenueCatWebhookRequest;
import org.son.haruharu.domain.payment.business.PaymentService;
import org.son.haruharu.infrastructure.revenuecat.WebhookSecretVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@Tag(name = "결제(IAP)", description = "RevenueCat 인앱결제 · 코인 상품")
@RestController
@RequestMapping("/api/v1/iap")
// 결제 비활성(#309) — haruharu.payment.enabled=true 일 때만 빈 등록. 기본 false라 webhook 부재(404). 코드 보존.
@ConditionalOnProperty(prefix = "haruharu.payment", name = "enabled", havingValue = "true")
public class IapWebhookController {

    private final WebhookSecretVerifier webhookSecretVerifier;
    private final PaymentService paymentService;
    private final JsonMapper jsonMapper;

    public IapWebhookController(
            WebhookSecretVerifier webhookSecretVerifier,
            PaymentService paymentService,
            JsonMapper jsonMapper
    ) {
        this.webhookSecretVerifier = webhookSecretVerifier;
        this.paymentService = paymentService;
        this.jsonMapper = jsonMapper;
    }

    @Operation(summary = "RevenueCat 결제 Webhook (내부용)",
            description = "RevenueCat 서버가 결제·환불 시 호출하는 Webhook. 앱·프런트가 직접 호출하지 않는다. "
                    + "JWT가 아니라 대시보드에 설정한 공유 시크릿(Authorization 헤더)으로 인증한다. "
                    + "구매 이벤트는 코인 적립, 취소 이벤트는 코인 회수를 수행한다.")
    @SecurityRequirements // JWT 미적용 — 공유 시크릿 인증(Swagger 자물쇠 미표시)
    @PostMapping("/webhook")
    public ApiResponse<Void> handle(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody String rawBody
    ) {
        webhookSecretVerifier.verify(authorization);
        RevenueCatWebhookRequest request = jsonMapper.readValue(rawBody, RevenueCatWebhookRequest.class);
        paymentService.handleWebhook(request.toCommand(rawBody));
        return ApiResponse.ok(null);
    }
}
