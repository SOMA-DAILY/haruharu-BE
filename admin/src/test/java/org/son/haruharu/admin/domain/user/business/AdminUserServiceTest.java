package org.son.haruharu.admin.domain.user.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.user.business.dto.AdminUserDetailResponse;
import org.son.haruharu.admin.domain.user.business.dto.AdminUserSummary;
import org.son.haruharu.admin.domain.user.implement.AdminUserReader;
import org.son.haruharu.admin.domain.user.implement.UserParticipation;
import org.son.haruharu.admin.domain.user.implement.UserWithProfile;
import org.son.haruharu.admin.domain.user.storage.exception.AdminUserNotFoundException;
import org.son.haruharu.domain.ticket.implement.TicketWallet;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.payment.implement.IapStore;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock AdminUserReader adminUserReader;

    AdminUserService service;

    private final LocalDateTime joined = LocalDateTime.of(2026, 7, 1, 9, 0);

    @BeforeEach
    void setUp() {
        service = new AdminUserService(adminUserReader, java.time.Clock.systemDefaultZone());
    }

    private User user(Long id, UserStatus status) {
        return new User(id, status, LocalDateTime.of(2026, 6, 30, 12, 0));
    }

    private UserProfile profile(Long userId, String nickname) {
        return new UserProfile(1L, userId, "서울", "직장인", 168, "INFP", nickname, null, null, null);
    }

    @Test
    @DisplayName("유저 목록은 닉네임·지역·상태·가입일을 담고 페이지 메타를 반환한다(프로필 없으면 null)")
    void getUsers_mapsSummaryWithProfileAndPageMeta() {
        given(adminUserReader.findUsers(UserStatus.ACTIVE, "여울", 0, 20)).willReturn(List.of(
                new UserWithProfile(user(901L, UserStatus.ACTIVE), profile(901L, "여울")),
                new UserWithProfile(user(902L, UserStatus.ACTIVE), null)));
        given(adminUserReader.countUsers(UserStatus.ACTIVE, "여울")).willReturn(2L);

        PageResponse<AdminUserSummary> result = service.getUsers(UserStatus.ACTIVE, "여울", 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).nickname()).isEqualTo("여울");
        assertThat(result.content().get(0).region()).isEqualTo("서울");
        assertThat(result.content().get(1).nickname()).isNull();
        assertThat(result.page().totalElements()).isEqualTo(2L);
    }

    @Test
    @DisplayName("유저 상세는 프로필·티켓잔액·결제이력·참여이력을 조합해 반환한다")
    void getUser_assemblesDetail() {
        given(adminUserReader.getById(901L)).willReturn(user(901L, UserStatus.ACTIVE));
        given(adminUserReader.profileOf(901L)).willReturn(profile(901L, "여울"));
        given(adminUserReader.walletOf(901L)).willReturn(new TicketWallet(1L, 901L, 3L, joined));
        given(adminUserReader.purchasesOf(901L)).willReturn(List.of(
                IapPurchase.owned(901L, 1L, "ticket_1", "tx1", IapStore.APP_STORE, 100, joined)));
        given(adminUserReader.participationsOf(901L)).willReturn(List.of(
                new UserParticipation(new Room(5L, RoomStatus.ENDED, LocalDate.of(2026, 6, 24), 3, null, null), joined)));

        AdminUserDetailResponse result = service.getUser(901L);

        assertThat(result.userId()).isEqualTo(901L);
        assertThat(result.profile().nickname()).isEqualTo("여울");
        assertThat(result.ticket().balance()).isEqualTo(3L);
        assertThat(result.payments()).hasSize(1);
        assertThat(result.payments().get(0).storeProductId()).isEqualTo("ticket_1");
        assertThat(result.participations()).hasSize(1);
        assertThat(result.participations().get(0).roomId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("티켓 지갑·프로필이 없으면 잔액 0·프로필 null로 반환한다")
    void getUser_whenNoWalletOrProfile_returnsZeroAndNull() {
        given(adminUserReader.getById(901L)).willReturn(user(901L, UserStatus.ONBOARDING));
        given(adminUserReader.profileOf(901L)).willReturn(null);
        given(adminUserReader.walletOf(901L)).willReturn(null);
        given(adminUserReader.purchasesOf(901L)).willReturn(List.of());
        given(adminUserReader.participationsOf(901L)).willReturn(List.of());

        AdminUserDetailResponse result = service.getUser(901L);

        assertThat(result.profile()).isNull();
        assertThat(result.ticket().balance()).isZero();
        assertThat(result.payments()).isEmpty();
    }

    @Test
    @DisplayName("없는 유저 상세 조회 시 AdminUserNotFoundException(404)")
    void getUser_whenNotFound_throws() {
        given(adminUserReader.getById(99L)).willThrow(new AdminUserNotFoundException());

        assertThatThrownBy(() -> service.getUser(99L))
                .isInstanceOf(AdminUserNotFoundException.class);
    }
}
