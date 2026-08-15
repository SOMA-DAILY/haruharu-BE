package org.son.haruharu.domain.payment.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.payment.business.PaymentService;
import org.son.haruharu.domain.payment.business.dto.ProductsResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제(IAP)", description = "RevenueCat 인앱결제 · 코인 상품")
@RestController
@RequestMapping("/api/v1/coin")
// 결제 비활성(#309) — haruharu.payment.enabled=true 일 때만 빈 등록. 기본 false라 상품 API 부재(404). 코드 보존.
@ConditionalOnProperty(prefix = "haruharu.payment", name = "enabled", havingValue = "true")
public class IapProductController {

    private final PaymentService paymentService;

    public IapProductController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "코인 상품 목록 조회",
            description = "충전 가능한 코인 상품 목록(활성 상품만)을 반환한다. 프런트는 각 상품의 store_product_id로 RevenueCat 상품과 매칭한다.")
    @GetMapping("/products")
    public ApiResponse<ProductsResponse> getProducts() {
        return ApiResponse.ok(paymentService.getProducts());
    }
}
