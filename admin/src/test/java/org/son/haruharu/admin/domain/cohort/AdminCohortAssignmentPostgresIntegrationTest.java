package org.son.haruharu.admin.domain.cohort;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.cohort.business.CohortAssignmentService;
import org.son.haruharu.domain.cohort.business.exception.CohortApplicationNotAssignableException;
import org.son.haruharu.domain.cohort.implement.ApplicantGender;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.storage.CohortApplicationEntity;
import org.son.haruharu.domain.cohort.storage.CohortApplicationJpaRepository;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomComposition;
import org.son.haruharu.domain.room.implement.RoomWriter;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** #516 실제 PostgreSQL: 같은 신청을 두 방에 동시에 넣으면 하나만 된다(방 → 신청 잠금 + 조건부 UPDATE). */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminCohortAssignmentPostgresIntegrationTest {

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

    @Autowired CohortAssignmentService assignmentService;
    @Autowired RoomWriter roomWriter;
    @Autowired CohortApplicationJpaRepository applications;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은 신청을 두 방에 동시에 넣으면 한 방만 성공하고 멤버도 한 명만 생긴다")
    void givenSameApplication_whenAssignConcurrently_thenOnlyOneWins() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        List<Long> rooms = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            rooms.add(roomWriter.save(Room.createScheduled(now.plusDays(1), now.plusDays(3), now.plusDays(3).plusHours(2),
                    now.plusDays(3).plusHours(3), now.plusDays(4), 3,
                    new RoomComposition(3L, "조" + i, 3, 3))).id());
        }
        jdbcTemplate.update("insert into users (id, status, created_at, updated_at) values (901, 'ACTIVE', now(), now())");
        Long applicationId = applications.saveAndFlush(CohortApplicationEntity.from(new CohortApplication(null, 901L, 3L,
                1L, CohortApplicationStatus.QUEUED, ApplicantGender.FEMALE, "FEMALE", LocalDate.of(2003, 5, 1), "한국대",
                "컴퓨터공학과", null, null, null, null, null, 0, now, now, null, null, null, null, null, null, null, null,
                now, now))).toDomain().id();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Throwable>> results = new ArrayList<>();
        for (Long roomId : rooms) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    assignmentService.assign(roomId, List.of(applicationId), "admin:1");
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
        }
        start.countDown();
        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> result : results) {
            Throwable failure = result.get();
            if (failure != null) failures.add(failure);
        }
        pool.shutdown();

        assertThat(failures).singleElement().isInstanceOf(CohortApplicationNotAssignableException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from room_members where user_id = 901", Long.class))
                .isEqualTo(1L);
        assertThat(applications.findById(applicationId).orElseThrow().toDomain().status())
                .isEqualTo(CohortApplicationStatus.ASSIGNED);
    }
}
