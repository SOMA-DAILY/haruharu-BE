package org.son.haruharu.admin.domain.user.implement;

import org.son.haruharu.admin.domain.user.storage.AdminUserQueryRepository;
import org.son.haruharu.admin.domain.user.storage.exception.AdminUserNotFoundException;
import org.son.haruharu.domain.coin.implement.CoinWallet;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/** 어드민 유저 조회 협력자. 트랜잭션 경계는 Service가 갖는다. */
@Component
public class AdminUserReader {

    private final AdminUserQueryRepository adminUserQueryRepository;

    public AdminUserReader(AdminUserQueryRepository adminUserQueryRepository) {
        this.adminUserQueryRepository = adminUserQueryRepository;
    }

    public List<UserWithProfile> findUsers(UserStatus status, String keyword, int offset, int limit) {
        return adminUserQueryRepository.findUsers(status, keyword, offset, limit);
    }

    public long countUsers(UserStatus status, String keyword) {
        return adminUserQueryRepository.countUsers(status, keyword);
    }

    public User getById(Long userId) {
        return adminUserQueryRepository.findUser(userId)
                .orElseThrow(AdminUserNotFoundException::new);
    }

    public UserProfile profileOf(Long userId) {
        return adminUserQueryRepository.findProfile(userId).orElse(null);
    }

    public CoinWallet walletOf(Long userId) {
        return adminUserQueryRepository.findWallet(userId).orElse(null);
    }

    public List<IapPurchase> purchasesOf(Long userId) {
        return adminUserQueryRepository.findPurchases(userId);
    }

    public List<UserParticipation> participationsOf(Long userId) {
        return adminUserQueryRepository.findParticipations(userId);
    }
}
