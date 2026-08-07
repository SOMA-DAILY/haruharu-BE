package org.son.haruharu.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.user.business.UserService;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 데모 프로비저닝을 실제 스택(H2)으로 검증(#264). identity 암호화·coin 원장까지 실경로로 통과시켜
 * demo_mode·본인인증 통과·구름 지급이 정합하게 반영되고 재실행이 멱등인지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class DemoProvisionIntegrationTest {

    @Autowired UserService userService;
    @Autowired IdentityService identityService;
    @Autowired CoinService coinService;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final LocalDate BIRTHDAY = LocalDate.of(1995, 6, 15);

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM coin_ledger");
        jdbcTemplate.update("DELETE FROM coin_wallet");
        jdbcTemplate.update("DELETE FROM user_identities");
        jdbcTemplate.update("DELETE FROM users");
    }

    private Long saveUser() {
        return userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ONBOARDING, null, false)))
                .toDomain().id();
    }

    @Test
    @DisplayName("프로비저닝 후 계정이 데모·ACTIVE·본인인증 통과·구름 200이 된다")
    void givenUser_whenProvision_thenDemoActiveVerifiedFunded() {
        Long userId = saveUser();

        userService.provisionDemo(userId, "MALE", BIRTHDAY, 200L);

        User user = userJpaRepository.findById(userId).orElseThrow().toDomain();
        assertThat(user.demoMode()).isTrue();
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(identityService.isVerified(userId)).isTrue();
        assertThat(coinService.getWallet(userId).totalBalance()).isEqualTo(200L);
    }

    @Test
    @DisplayName("재프로비저닝은 멱등이다 — identity 중복·구름 중복 지급이 없다")
    void givenProvisioned_whenProvisionAgain_thenIdempotent() {
        Long userId = saveUser();
        userService.provisionDemo(userId, "MALE", BIRTHDAY, 200L);

        userService.provisionDemo(userId, "MALE", BIRTHDAY, 200L);

        assertThat(identityService.isVerified(userId)).isTrue();
        assertThat(coinService.getWallet(userId).totalBalance()).isEqualTo(200L); // 400 아님
    }

    @Test
    @DisplayName("실결제(PURCHASE) 이력이 있는 계정은 데모 프로비저닝을 거부하고 아무것도 바꾸지 않는다(#277)")
    void givenPurchaseHistory_whenProvision_thenRejectedAndUnchanged() {
        Long userId = saveUser();
        // 지갑은 가입 시점에 생성된다(#298). saveUser는 가입 경로를 타지 않으므로 직접 만든다.
        coinService.createWallet(userId);
        // 실경로로 IAP 구매 원장을 남긴다(reason=PURCHASE).
        coinService.credit(org.son.haruharu.domain.coin.business.command.CoinCreditCommand
                .purchase(userId, 100L, "IAP", "txn-1"));

        assertThat(coinService.hasPurchaseHistory(userId)).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> userService.provisionDemo(userId, "MALE", BIRTHDAY, 200L))
                .isInstanceOf(IllegalStateException.class);

        // demo_mode 전환·본인인증 시드·데모 구름 지급이 일어나지 않았다(결제 잔액만 유지).
        User user = userJpaRepository.findById(userId).orElseThrow().toDomain();
        assertThat(user.demoMode()).isFalse();
        assertThat(identityService.isVerified(userId)).isFalse();
        assertThat(coinService.getWallet(userId).totalBalance()).isEqualTo(100L); // 데모 200 미지급
    }
}
