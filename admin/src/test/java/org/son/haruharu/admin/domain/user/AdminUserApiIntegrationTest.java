package org.son.haruharu.admin.domain.user;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.coin.implement.CoinWallet;
import org.son.haruharu.domain.coin.storage.CoinWalletEntity;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.payment.implement.IapStore;
import org.son.haruharu.domain.payment.storage.IapPurchaseEntity;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
class AdminUserApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    @PersistenceContext
    EntityManager em;

    private static UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_CS")));
    }

    private Long persistUser(UserStatus status) {
        UserEntity entity = UserEntity.from(new User(null, status, null));
        em.persist(entity);
        return entity.toDomain().id();
    }

    @Test
    @DisplayName("keyword로 닉네임 검색 시 매칭 유저만 페이지로 반환된다")
    void users_searchByNickname() throws Exception {
        Long yeoul = persistUser(UserStatus.ACTIVE);
        Long other = persistUser(UserStatus.ACTIVE);
        em.persist(UserProfileEntity.from(new UserProfile(null, yeoul, "서울", "직장인", 168, "INFP", "여울", null, null)));
        em.persist(UserProfileEntity.from(new UserProfile(null, other, "부산", "학생", 180, "ENTP", "바다", null, null)));
        em.flush();

        mockMvc.perform(get("/api/v1/admin/users").param("keyword", "여울").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].user_id").value(yeoul))
                .andExpect(jsonPath("$.data.content[0].nickname").value("여울"))
                .andExpect(jsonPath("$.data.content[0].region").value("서울"));
    }

    @Test
    @DisplayName("유저 상세는 프로필·코인잔액·결제이력·참여이력을 조합해 반환한다")
    void userDetail_assemblesProfileCoinPaymentParticipation() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Long userId = persistUser(UserStatus.ACTIVE);
        em.persist(UserProfileEntity.from(new UserProfile(null, userId, "서울", "직장인", 168, "INFP", "여울", null, null)));
        em.persist(CoinWalletEntity.from(new CoinWallet(null, userId, 100L, 50L, null)));
        em.persist(IapPurchaseEntity.from(IapPurchase.owned(userId, 1L, "coin_100", "tx-1", IapStore.APP_STORE, 100, now)));
        RoomEntity room = RoomEntity.from(Room.create(LocalDate.now(), 3).started(now));
        em.persist(room);
        Long roomId = room.toDomain().id();
        em.persist(RoomMemberEntity.from(RoomMember.of(roomId, userId, "FEMALE", LocalDate.of(2000, 1, 1), now)));
        em.flush();

        mockMvc.perform(get("/api/v1/admin/users/{userId}", userId).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_id").value(userId))
                .andExpect(jsonPath("$.data.profile.nickname").value("여울"))
                .andExpect(jsonPath("$.data.coin.paid").value(100))
                .andExpect(jsonPath("$.data.coin.total").value(150))
                .andExpect(jsonPath("$.data.payments.length()").value(1))
                .andExpect(jsonPath("$.data.payments[0].store_product_id").value("coin_100"))
                .andExpect(jsonPath("$.data.participations.length()").value(1))
                .andExpect(jsonPath("$.data.participations[0].room_id").value(roomId));
    }

    @Test
    @DisplayName("없는 유저 상세 조회 시 404")
    void userDetail_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/{userId}", 999999L).with(authentication(adminAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADMIN_USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 없이 유저 목록 접근 시 401")
    void users_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }
}
