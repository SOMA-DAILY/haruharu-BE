package org.son.haruharu.admin.domain.payment.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.payment.business.AdminPurchaseService;
import org.son.haruharu.admin.domain.payment.business.dto.AdminPurchaseDetailResponse;
import org.son.haruharu.admin.domain.payment.business.dto.AdminPurchaseSummary;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.payment.implement.IapPurchaseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin IAP Purchase", description = "인앱결제 내역 조회")
@RestController
@RequestMapping("/api/v1/admin/iap/purchases")
public class AdminPurchaseController {

    private final AdminPurchaseService adminPurchaseService;

    public AdminPurchaseController(AdminPurchaseService adminPurchaseService) {
        this.adminPurchaseService = adminPurchaseService;
    }

    @Operation(summary = "결제 내역 목록", description = "status·userId·storeProductId 필터 + 페이지네이션(최신순). 각 행에 구매자 닉네임 포함.")
    @GetMapping
    public ApiResponse<PageResponse<AdminPurchaseSummary>> list(
            @Parameter(description = "상태 필터", example = "OWNED") @RequestParam(required = false) IapPurchaseStatus status,
            @Parameter(description = "구매 유저 id 필터", example = "42") @RequestParam(required = false) Long userId,
            @Parameter(description = "스토어 상품 id 필터", example = "ticket_1") @RequestParam(required = false) String storeProductId,
            @Parameter(description = "0-based 페이지", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기(최대 100)", example = "20") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminPurchaseService.getPurchases(status, userId, storeProductId, page, size));
    }

    @Operation(summary = "결제 내역 상세", description = "단건 + 스토어 거래 식별자. 없는 id는 404.")
    @GetMapping("/{id}")
    public ApiResponse<AdminPurchaseDetailResponse> detail(
            @Parameter(description = "구매 id", example = "1") @PathVariable Long id) {
        return ApiResponse.ok(adminPurchaseService.getPurchase(id));
    }
}
