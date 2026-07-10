package org.son.haruharu.admin.domain.user.business;

import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.user.business.dto.AdminUserDetailResponse;
import org.son.haruharu.admin.domain.user.business.dto.AdminUserSummary;
import org.son.haruharu.admin.domain.user.implement.AdminUserReader;
import org.son.haruharu.admin.domain.user.implement.UserWithProfile;
import org.son.haruharu.domain.coin.implement.CoinWallet;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 어드민 유저 조회. 목록(검색·필터·페이지네이션)·상세(프로필·재화·결제·참여이력). */
@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserReader adminUserReader;

    public AdminUserService(AdminUserReader adminUserReader) {
        this.adminUserReader = adminUserReader;
    }

    public PageResponse<AdminUserSummary> getUsers(UserStatus status, String keyword, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        List<AdminUserSummary> content = adminUserReader.findUsers(status, keyword, safePage * safeSize, safeSize).stream()
                .map(this::toSummary)
                .toList();
        long total = adminUserReader.countUsers(status, keyword);
        return PageResponse.of(content, safePage, safeSize, total);
    }

    public AdminUserDetailResponse getUser(Long userId) {
        User user = adminUserReader.getById(userId);
        UserProfile profile = adminUserReader.profileOf(userId);
        CoinWallet wallet = adminUserReader.walletOf(userId);

        return new AdminUserDetailResponse(
                user.id(), user.status(), user.createdAt(),
                toProfileInfo(profile), toCoinInfo(wallet),
                adminUserReader.purchasesOf(userId).stream().map(this::toPaymentInfo).toList(),
                adminUserReader.participationsOf(userId).stream()
                        .map(p -> new AdminUserDetailResponse.ParticipationInfo(
                                p.room().id(), p.room().status(), p.room().scheduledDate(), p.joinedAt()))
                        .toList());
    }

    private AdminUserSummary toSummary(UserWithProfile row) {
        UserProfile profile = row.profile();
        User user = row.user();
        return new AdminUserSummary(
                user.id(),
                profile == null ? null : profile.nickname(),
                profile == null ? null : profile.location(),
                user.status(), user.createdAt());
    }

    private AdminUserDetailResponse.ProfileInfo toProfileInfo(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        return new AdminUserDetailResponse.ProfileInfo(
                profile.nickname(), profile.location(), profile.mbti(), profile.affiliation(), profile.height());
    }

    private AdminUserDetailResponse.CoinInfo toCoinInfo(CoinWallet wallet) {
        if (wallet == null) {
            return new AdminUserDetailResponse.CoinInfo(0, 0, 0);
        }
        return new AdminUserDetailResponse.CoinInfo(
                wallet.paidBalance(), wallet.rewardBalance(), wallet.totalBalance());
    }

    private AdminUserDetailResponse.PaymentInfo toPaymentInfo(IapPurchase purchase) {
        return new AdminUserDetailResponse.PaymentInfo(
                purchase.id(), purchase.storeProductId(), purchase.coinAmount(), purchase.store(),
                purchase.status(), purchase.purchasedAt(), purchase.refundedAt());
    }
}
