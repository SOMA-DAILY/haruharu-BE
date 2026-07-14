package org.son.haruharu.admin.domain.product.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.admin.domain.product.application.dto.CreateIapProductRequest;
import org.son.haruharu.admin.domain.product.application.dto.UpdateIapProductRequest;
import org.son.haruharu.admin.domain.product.business.AdminIapProductService;
import org.son.haruharu.admin.domain.product.business.dto.AdminIapProductResponse;
import org.son.haruharu.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin IAP Product", description = "코인 상품 카탈로그 관리(SUPER 전용)")
@RestController
@RequestMapping("/api/v1/admin/iap/products")
public class AdminIapProductController {

    private final AdminIapProductService adminIapProductService;

    public AdminIapProductController(AdminIapProductService adminIapProductService) {
        this.adminIapProductService = adminIapProductService;
    }

    @Operation(summary = "코인 상품 생성", description = "storeProductId 중복 시 409. SUPER 전용.")
    @PostMapping
    public ApiResponse<AdminIapProductResponse> create(@RequestBody @Valid CreateIapProductRequest request) {
        return ApiResponse.ok(adminIapProductService.create(request.toCommand()));
    }

    @Operation(summary = "코인 상품 목록", description = "활성/비활성 전체. SUPER 전용.")
    @GetMapping
    public ApiResponse<List<AdminIapProductResponse>> list() {
        return ApiResponse.ok(adminIapProductService.getProducts());
    }

    @Operation(summary = "코인 상품 수정", description = "보낸 필드만 부분 갱신(코인·보너스·가격·통화·활성). SUPER 전용.")
    @PatchMapping("/{id}")
    public ApiResponse<AdminIapProductResponse> update(
            @Parameter(description = "상품 id", example = "1") @PathVariable Long id,
            @RequestBody @Valid UpdateIapProductRequest request) {
        return ApiResponse.ok(adminIapProductService.update(id, request.toCommand()));
    }
}
