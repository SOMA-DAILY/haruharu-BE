package org.son.haruharu.admin.domain.payment.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.payment.business.dto.AdminPurchaseDetailResponse;
import org.son.haruharu.admin.domain.payment.business.dto.AdminPurchaseSummary;
import org.son.haruharu.admin.domain.payment.implement.AdminPurchaseReader;
import org.son.haruharu.admin.domain.payment.storage.exception.AdminPurchaseNotFoundException;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.payment.implement.IapPurchaseStatus;
import org.son.haruharu.domain.payment.implement.IapStore;
import org.son.haruharu.domain.profile.implement.UserProfile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminPurchaseServiceTest {

    @Mock AdminPurchaseReader adminPurchaseReader;

    AdminPurchaseService service;

    @BeforeEach
    void setUp() {
        service = new AdminPurchaseService(adminPurchaseReader);
    }

    private IapPurchase purchase(Long id, Long userId) {
        return new IapPurchase(id, userId, 3L, "groom_60", "txn-" + id, IapStore.APP_STORE,
                60, IapPurchaseStatus.OWNED, LocalDateTime.of(2026, 7, 15, 10, 0), null);
    }

    private UserProfile profile(Long userId, String nickname) {
        return new UserProfile(userId, userId, "서울", "직장인", 170, "INFP", nickname, null, null, null);
    }

    @Test
    @DisplayName("목록은 구매자 닉네임을 조인하고 페이지 메타를 채운다")
    void getPurchases_enrichesNicknameAndPaging() {
        given(adminPurchaseReader.findPurchases(eq(IapPurchaseStatus.OWNED), eq(null), eq(null), eq(0), eq(20)))
                .willReturn(List.of(purchase(2L, 42L), purchase(1L, 43L)));
        given(adminPurchaseReader.countPurchases(IapPurchaseStatus.OWNED, null, null)).willReturn(2L);
        given(adminPurchaseReader.profilesOf(any()))
                .willReturn(List.of(profile(42L, "하루"), profile(43L, "이틀")));

        PageResponse<AdminPurchaseSummary> result =
                service.getPurchases(IapPurchaseStatus.OWNED, null, null, 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).userId()).isEqualTo(42L);
        assertThat(result.content().get(0).nickname()).isEqualTo("하루");
        assertThat(result.content().get(1).nickname()).isEqualTo("이틀");
        assertThat(result.page().totalElements()).isEqualTo(2L);
    }

    @Test
    @DisplayName("프로필이 없는 구매자는 닉네임이 null로 채워진다")
    void getPurchases_missingProfile_nicknameNull() {
        given(adminPurchaseReader.findPurchases(any(), any(), any(), eq(0), eq(20)))
                .willReturn(List.of(purchase(1L, 99L)));
        given(adminPurchaseReader.countPurchases(any(), any(), any())).willReturn(1L);
        given(adminPurchaseReader.profilesOf(any())).willReturn(List.of()); // 프로필 없음

        PageResponse<AdminPurchaseSummary> result = service.getPurchases(null, null, null, 0, 20);

        assertThat(result.content().get(0).nickname()).isNull();
    }

    @Test
    @DisplayName("size가 최대치(100)를 넘으면 100으로 제한된다")
    void getPurchases_clampsPageSize() {
        given(adminPurchaseReader.findPurchases(any(), any(), any(), eq(0), eq(100)))
                .willReturn(List.of());
        given(adminPurchaseReader.countPurchases(any(), any(), any())).willReturn(0L);
        given(adminPurchaseReader.profilesOf(any())).willReturn(List.of());

        PageResponse<AdminPurchaseSummary> result = service.getPurchases(null, null, null, 0, 500);

        assertThat(result.page().size()).isEqualTo(100);
    }

    @Test
    @DisplayName("상세는 구매 + 닉네임 + 스토어 거래 id를 반환한다")
    void getPurchase_returnsDetail() {
        given(adminPurchaseReader.getById(1L)).willReturn(purchase(1L, 42L));
        given(adminPurchaseReader.profilesOf(List.of(42L))).willReturn(List.of(profile(42L, "하루")));

        AdminPurchaseDetailResponse result = service.getPurchase(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.nickname()).isEqualTo("하루");
        assertThat(result.storeTransactionId()).isEqualTo("txn-1");
    }

    @Test
    @DisplayName("없는 구매 상세 조회 시 AdminPurchaseNotFoundException(404)")
    void getPurchase_notFound_throws() {
        given(adminPurchaseReader.getById(99L)).willThrow(new AdminPurchaseNotFoundException());

        assertThatThrownBy(() -> service.getPurchase(99L))
                .isInstanceOf(AdminPurchaseNotFoundException.class);
    }
}
