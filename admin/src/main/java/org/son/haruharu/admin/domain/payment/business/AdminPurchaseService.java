package org.son.haruharu.admin.domain.payment.business;

import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.payment.business.dto.AdminPurchaseDetailResponse;
import org.son.haruharu.admin.domain.payment.business.dto.AdminPurchaseSummary;
import org.son.haruharu.admin.domain.payment.implement.AdminPurchaseReader;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.payment.implement.IapPurchaseStatus;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 어드민 결제(IAP) 내역 조회. 목록(필터·페이지네이션·닉네임 enrich)·상세. */
@Service
@Transactional(readOnly = true)
public class AdminPurchaseService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminPurchaseReader adminPurchaseReader;

    public AdminPurchaseService(AdminPurchaseReader adminPurchaseReader) {
        this.adminPurchaseReader = adminPurchaseReader;
    }

    public PageResponse<AdminPurchaseSummary> getPurchases(IapPurchaseStatus status, Long userId,
                                                           String storeProductId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        List<IapPurchase> purchases = adminPurchaseReader
                .findPurchases(status, userId, storeProductId, safePage * safeSize, safeSize);
        long total = adminPurchaseReader.countPurchases(status, userId, storeProductId);

        Map<Long, String> nicknameByUserId = nicknamesOf(purchases);
        List<AdminPurchaseSummary> content = purchases.stream()
                .map(p -> AdminPurchaseSummary.of(p, nicknameByUserId.get(p.userId())))
                .toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    public AdminPurchaseDetailResponse getPurchase(Long id) {
        IapPurchase purchase = adminPurchaseReader.getById(id);
        String nickname = nicknamesOf(List.of(purchase)).get(purchase.userId());
        return AdminPurchaseDetailResponse.of(purchase, nickname);
    }

    /** 구매자 userId → 닉네임 맵. 프로필 미설정 유저는 맵에 없음(null enrich). */
    private Map<Long, String> nicknamesOf(List<IapPurchase> purchases) {
        List<Long> userIds = purchases.stream().map(IapPurchase::userId).distinct().toList();
        return adminPurchaseReader.profilesOf(userIds).stream()
                .collect(Collectors.toMap(UserProfile::userId, UserProfile::nickname, (a, b) -> a));
    }
}
