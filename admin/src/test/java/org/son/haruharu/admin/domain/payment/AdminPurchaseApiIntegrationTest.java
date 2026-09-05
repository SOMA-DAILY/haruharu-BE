package org.son.haruharu.admin.domain.payment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.payment.implement.IapStore;
import org.son.haruharu.domain.payment.storage.IapPurchaseEntity;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminPurchaseApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    @PersistenceContext
    EntityManager em;

    private static UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_CS")));
    }

    private Long persistPurchase(Long userId, String storeProductId, String txnId, LocalDateTime purchasedAt) {
        IapPurchaseEntity entity = IapPurchaseEntity.from(IapPurchase.owned(
                userId, 3L, storeProductId, txnId, IapStore.APP_STORE, 60, purchasedAt));
        em.persist(entity);
        return entity.toDomain().id();
    }

    @Test
    @DisplayName("목록은 최신순 페이지로 반환되고 구매자 닉네임이 담긴다")
    void purchases_list_returnsWithNickname() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        persistPurchase(901L, "groom_60", "txn-a", now.minusHours(2));
        Long recent = persistPurchase(902L, "groom_200", "txn-b", now.minusHours(1));
        em.persist(UserProfileEntity.from(new UserProfile(null, 902L, "서울", "직장인", 170, "INFP", "여울", null, null, null)));
        em.flush();

        mockMvc.perform(get("/api/v1/admin/iap/purchases")
                        .param("page", "0").param("size", "20")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(recent)) // 최신순
                .andExpect(jsonPath("$.data.content[0].nickname").value("여울"))
                .andExpect(jsonPath("$.data.page.total_elements").value(2));
    }

    @Test
    @DisplayName("storeProductId 필터 시 해당 상품 구매만 반환된다")
    void purchases_filterByStoreProductId() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        persistPurchase(901L, "groom_60", "txn-a", now);
        persistPurchase(902L, "groom_200", "txn-b", now);
        em.flush();

        mockMvc.perform(get("/api/v1/admin/iap/purchases")
                        .param("storeProductId", "groom_200")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].store_product_id").value("groom_200"));
    }

    @Test
    @DisplayName("상세는 스토어 거래 id를 포함해 반환된다")
    void purchaseDetail_returnsTransactionId() throws Exception {
        Long id = persistPurchase(901L, "groom_60", "txn-detail", LocalDateTime.now());
        em.flush();

        mockMvc.perform(get("/api/v1/admin/iap/purchases/{id}", id).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.store_transaction_id").value("txn-detail"));
    }

    @Test
    @DisplayName("없는 구매 상세 조회 시 404")
    void purchaseDetail_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/iap/purchases/{id}", 999999L).with(authentication(adminAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADMIN_PURCHASE_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 없이 접근 시 401")
    void purchases_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/iap/purchases"))
                .andExpect(status().isUnauthorized());
    }
}
