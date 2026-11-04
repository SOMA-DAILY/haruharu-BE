package org.son.haruharu.admin.domain.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.admin.domain.audit.implement.AdminAccessLog;
import org.son.haruharu.admin.domain.audit.implement.AdminAccessLogRepository;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditAction;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditCount;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditDailyCount;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditTargetType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** #554 native 집계(SUM/CAST AS DATE)를 실제 PostgreSQL 드라이버 타입으로 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminAccessLogSummaryPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired AdminAccessLogRepository repository;

    @Test
    @DisplayName("관리자별 조회·반출 수와 날짜별 조회 수가 PostgreSQL 에서 올바른 타입으로 집계된다")
    void givenRows_whenAggregate_thenCountsAndDates() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 3, 10, 0);
        repository.save(AdminAccessLog.of(1L, base, "10.0.0.1", "GET", "/api/v1/admin/users/1",
                AdminAuditAction.VIEW_USER, AdminAuditTargetType.USER, 1L, 200, null, null, null));
        repository.save(AdminAccessLog.of(1L, base.plusHours(1), "10.0.0.1", "GET", "/api/v1/admin/rooms/7",
                AdminAuditAction.VIEW_ROOM, AdminAuditTargetType.ROOM, 7L, 200, null, null, null));
        repository.save(AdminAccessLog.of(1L, base.plusDays(1), "10.0.0.1", "POST", "/api/v1/admin/audit/exports",
                AdminAuditAction.EXPORT, AdminAuditTargetType.ROOM, 7L, 200, "조사용 반출", null, null));
        repository.save(AdminAccessLog.of(null, base, "10.0.0.9", "POST", "/api/v1/admin/auth/login",
                AdminAuditAction.LOGIN_FAIL, null, null, 401, null, "email=x@y.z", null));

        List<AdminAuditCount> counts = repository.countByAdmin(base.minusDays(1), base.plusDays(2));
        assertThat(counts).containsExactly(new AdminAuditCount(1L, 2, 1));

        List<AdminAuditDailyCount> days = repository.findHeavyViewDays(base.minusDays(1), base.plusDays(2), 2);
        assertThat(days).containsExactly(new AdminAuditDailyCount(1L, LocalDate.of(2026, 11, 3), 2));
        assertThat(repository.findHeavyViewDays(base.minusDays(1), base.plusDays(2), 3)).isEmpty();
    }
}
