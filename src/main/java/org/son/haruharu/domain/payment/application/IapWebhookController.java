package org.son.haruharu.domain.payment.application;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.payment.application.dto.RevenueCatWebhookRequest;
import org.son.haruharu.domain.payment.business.PaymentService;
import org.son.haruharu.infrastructure.revenuecat.WebhookSecretVerifier;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/iap")
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
