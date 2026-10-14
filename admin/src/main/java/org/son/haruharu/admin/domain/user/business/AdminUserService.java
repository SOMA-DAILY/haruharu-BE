package org.son.haruharu.admin.domain.user.business;

import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.user.business.dto.AdminUserDetailResponse;
import org.son.haruharu.admin.domain.user.business.dto.AdminUserRoomHistoryResponse;
import org.son.haruharu.admin.domain.user.business.dto.AdminUserSummary;
import org.son.haruharu.admin.domain.user.implement.AdminUserReader;
import org.son.haruharu.admin.domain.user.implement.UserRoomHistory;
import org.son.haruharu.admin.domain.user.implement.UserWithProfile;
import org.son.haruharu.domain.ticket.implement.TicketWallet;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 어드민 유저 조회. 목록(검색·필터·페이지네이션)·상세(프로필·재화·결제·참여이력)·방 참여 이력(선택·활동). */
@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    /** 방 안 가명이 없을 때 표기 — 방 선택 패널(AdminRoomSelectionReader)과 같은 문구. */
    private static final String NO_PSEUDONYM = "가명 없음";

    private final AdminUserReader adminUserReader;
    private final Clock clock;

    public AdminUserService(AdminUserReader adminUserReader, Clock clock) {
        this.adminUserReader = adminUserReader;
        this.clock = clock;
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
        LocalDateTime now = LocalDateTime.now(clock);
        UserProfile profile = adminUserReader.profileOf(userId);
        TicketWallet wallet = adminUserReader.walletOf(userId);

        return new AdminUserDetailResponse(
                user.id(), user.status(), user.createdAt(),
                toProfileInfo(profile), toTicketInfo(wallet),
                adminUserReader.purchasesOf(userId).stream().map(this::toPaymentInfo).toList(),
                adminUserReader.participationsOf(userId).stream()
                        .map(p -> new AdminUserDetailResponse.ParticipationInfo(
                                // 표시 status 는 파생값 — 저장 컬럼은 배치 지연에 노출된다(방 목록·상세와 동일, #320).
                                p.room().id(), p.room().statusAt(now), p.room().scheduledDate(), p.joinedAt()))
                        .toList());
    }

    /** 방 참여 이력(#508). 최신 입장순. 없는 유저는 404. */
    public List<AdminUserRoomHistoryResponse> getUserRooms(Long userId) {
        adminUserReader.getById(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        return adminUserReader.roomHistoryOf(userId).stream().map(h -> toRoomHistory(h, now)).toList();
    }

    private AdminUserRoomHistoryResponse toRoomHistory(UserRoomHistory history, LocalDateTime now) {
        Room room = history.room();
        RoomMember member = history.member();
        RoomStatus status = room.statusAt(now);
        RoomMember selected = history.selected();
        return new AdminUserRoomHistoryResponse(
                room.id(), status, room.scheduledDate(), room.totalDays(),
                // 새 모델 방은 예정 시각, 구 모델 방은 실제 시작·종료 시각
                room.startsAt() != null ? room.startsAt() : room.startedAt(),
                room.endsAt() != null ? room.endsAt() : room.endedAt(),
                member.joinedAt(), member.pseudonym(), member.gender(),
                history.submitted(),
                selected == null ? null : new AdminUserRoomHistoryResponse.Person(selected.userId(), nameOf(selected)),
                history.receivedCount(), history.outcomeAt(status), history.videoCount(), history.chatCount());
    }

    private String nameOf(RoomMember member) {
        return member.pseudonym() == null || member.pseudonym().isBlank() ? NO_PSEUDONYM : member.pseudonym();
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

    private AdminUserDetailResponse.TicketInfo toTicketInfo(TicketWallet wallet) {
        return new AdminUserDetailResponse.TicketInfo(wallet == null ? 0 : wallet.balance());
    }

    private AdminUserDetailResponse.PaymentInfo toPaymentInfo(IapPurchase purchase) {
        return new AdminUserDetailResponse.PaymentInfo(
                purchase.id(), purchase.storeProductId(), purchase.ticketAmount(), purchase.store(),
                purchase.status(), purchase.purchasedAt(), purchase.refundedAt());
    }
}
