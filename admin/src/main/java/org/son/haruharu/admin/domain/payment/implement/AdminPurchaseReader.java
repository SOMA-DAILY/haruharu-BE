package org.son.haruharu.admin.domain.payment.implement;

import org.son.haruharu.admin.domain.payment.storage.AdminPurchaseQueryRepository;
import org.son.haruharu.admin.domain.payment.storage.exception.AdminPurchaseNotFoundException;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.payment.implement.IapPurchaseStatus;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.springframework.stereotype.Component;

import java.util.List;

/** 어드민 결제(IAP) 내역 조회 협력자. 트랜잭션 경계는 Service가 갖는다. */
@Component
public class AdminPurchaseReader {

    private final AdminPurchaseQueryRepository adminPurchaseQueryRepository;

    public AdminPurchaseReader(AdminPurchaseQueryRepository adminPurchaseQueryRepository) {
        this.adminPurchaseQueryRepository = adminPurchaseQueryRepository;
    }

    public List<IapPurchase> findPurchases(IapPurchaseStatus status, Long userId, String storeProductId,
                                           int offset, int limit) {
        return adminPurchaseQueryRepository.findPurchases(status, userId, storeProductId, offset, limit);
    }

    public long countPurchases(IapPurchaseStatus status, Long userId, String storeProductId) {
        return adminPurchaseQueryRepository.countPurchases(status, userId, storeProductId);
    }

    public IapPurchase getById(Long id) {
        return adminPurchaseQueryRepository.findById(id)
                .orElseThrow(AdminPurchaseNotFoundException::new);
    }

    public List<UserProfile> profilesOf(List<Long> userIds) {
        return adminPurchaseQueryRepository.findProfilesByUserIds(userIds);
    }
}
