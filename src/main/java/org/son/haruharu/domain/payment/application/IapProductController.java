package org.son.haruharu.domain.payment.application;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.payment.business.PaymentService;
import org.son.haruharu.domain.payment.business.dto.ProductsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coin")
public class IapProductController {

    private final PaymentService paymentService;

    public IapProductController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/products")
    public ApiResponse<ProductsResponse> getProducts() {
        return ApiResponse.ok(paymentService.getProducts());
    }
}
