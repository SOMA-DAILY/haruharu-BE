package org.son.haruharu.domain.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.auth.business.AuthService;
import org.son.haruharu.domain.auth.business.command.TokenRefreshCommand;
import org.son.haruharu.domain.auth.business.dto.AuthTokenResponse;
import org.son.haruharu.domain.auth.business.exception.InvalidRefreshTokenException;
import org.son.haruharu.domain.auth.implement.RefreshToken;
import org.son.haruharu.domain.auth.implement.RefreshTokenWriter;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.son.haruharu.utils.TokenHashUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 리프레시 토큰의 동시 재발급을 실제 PostgreSQL 로 검증한다(#417).
 * H2 단일 스레드로는 "둘 다 활성 토큰을 읽고 둘 다 발급" 경쟁을 못 만든다. 조건부 UPDATE(revoked_at IS NULL)의
 * 행 락 대기 후 재평가는 PostgreSQL READ COMMITTED 동작이라 컨테이너로 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RefreshTokenPostgresConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired AuthService authService;
    @Autowired RefreshTokenWriter refreshTokenWriter;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired JdbcTemplate jdbcTemplate;

    private Long userId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE refresh_tokens, users RESTART IDENTITY CASCADE");
        userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
    }

    @Test
    @DisplayName("PostgreSQL에서 같은 토큰으로 동시에 재발급하면 정확히 하나만 새 토큰을 받고 나머지는 401이며 전체 세션은 유지된다")
    void givenSameToken_whenRefreshedConcurrently_thenExactlyOneSucceedsAndOtherSessionsSurvive() throws Exception {
        String shared = jwtProvider.generateRefreshToken(userId);
        store(shared);
        String otherDevice = jwtProvider.generateRefreshToken(userId);
        store(otherDevice);

        Queue<AuthTokenResponse> issued = new ConcurrentLinkedQueue<>();
        Queue<Throwable> rejected = new ConcurrentLinkedQueue<>();
        Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> {
                try {
                    issued.add(authService.refresh(new TokenRefreshCommand(shared)));
                } catch (InvalidRefreshTokenException e) {
                    rejected.add(e);
                } catch (Throwable t) {
                    unexpected.add(t);
                }
            });
        }

        runTogether(tasks);

        // 스레드 교차는 제어하지 않는다 — 패자가 조건부 UPDATE 0건(CONSUMED_CONCURRENTLY)으로 지든
        // 커밋된 revoke 를 읽고 grace(REVOKED_IN_GRACE)로 지든, 지켜야 할 불변식은 아래와 같다.
        assertThat(unexpected).isEmpty();               // 예전엔 여기서 DataIntegrityViolation(409) 이 났다
        assertThat(issued).hasSize(1);                   // 토큰 하나로 세션 하나만 파생
        assertThat(rejected).hasSize(3);
        Integer active = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Integer.class, userId);
        assertThat(active).isEqualTo(2);                 // 다른 기기 토큰 + 승자의 새 토큰 — 전체 폐기가 일어나지 않음
        assertThat(authService.refresh(new TokenRefreshCommand(otherDevice)).refreshToken()).isNotBlank();
    }

    private void store(String token) {
        refreshTokenWriter.save(new RefreshToken(null, userId, TokenHashUtils.sha256Hex(token),
                LocalDateTime.now().plusDays(30), null));
    }

    private void runTogether(List<Runnable> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks.size());
        try {
            for (Runnable task : tasks) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("start latch timed out");
                        }
                        task.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }
}
