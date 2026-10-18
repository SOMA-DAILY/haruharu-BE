package org.son.haruharu.domain.room;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.business.RoomInviteService;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotUsableException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomComposition;
import org.son.haruharu.domain.room.implement.RoomEntryPath;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #513 실제 PostgreSQL 검증. Hibernate 가 만든 스키마 위에 510·511·513 up 을 덮어 verify 를 통과하는지,
 * 기존 멤버가 INVITE 로 채워지는지, DB 제약이 입장 경로·신청 짝을 지키는지, 앱 신청자가 섞인 방에서
 * 동시 초대코드 입장이 웹 몫만큼만 성공하는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoomCohortCapacityPostgresIntegrationTest {

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

    @Autowired RoomInviteService roomInviteService;
    @Autowired RoomWriter roomWriter;
    @Autowired RoomReader roomReader;
    @Autowired RoomMemberWriter roomMemberWriter;
    @Autowired RoomMemberReader roomMemberReader;
    @Autowired JdbcTemplate jdbcTemplate;

    private long cohortId;
    private long trackId;

    @BeforeEach
    void applyMigrationsOverHibernateSchema() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE chat_messages, chat_room_members, chat_rooms, room_invites, room_members,"
                + " rooms, cohort_applications, cohorts, recruit_tracks RESTART IDENTITY CASCADE");
        for (String script : List.of("510-recruit-cohort-up", "511-cohort-application-up",
                "513-room-cohort-capacity-up", "513-room-cohort-capacity-verify")) {
            jdbcTemplate.execute(sql(script));
        }
        trackId = count("INSERT INTO recruit_tracks (track_key, label, region, audience, min_age, max_age, questions,"
                + " display_order, active, created_at, updated_at) VALUES ('seoul-student', '서울 대학생', '서울', 'STUDENT',"
                + " 20, 29, '[]', 1, true, now(), now()) RETURNING id");
        cohortId = count("INSERT INTO cohorts (track_id, sequence, status, apply_opens_at, apply_closes_at,"
                + " assign_deadline_at, period_label, created_at, updated_at) VALUES (?, 6, 'OPEN', now(), now() + interval '3 day',"
                + " now() + interval '4 day', '10월 마지막 주', now(), now()) RETURNING id", trackId);
    }

    private static String sql(String name) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("scripts/sql"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("Missing migration scripts");
        return Files.readString(root.resolve("scripts/sql/" + name + ".sql"));
    }

    /** 실패할 수 있는 스크립트는 풀 밖 연결로 돌린다. BEGIN 뒤 실패하면 연결이 중단된 트랜잭션으로 남기 때문이다. */
    private static void executeOutsidePool(String script) throws SQLException {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()); var statement = c.createStatement()) {
            statement.execute(script);
        }
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private long application(long userId) {
        return count("INSERT INTO cohort_applications (user_id, cohort_id, track_id, status, declared_gender, school,"
                + " department, answers, priority, applied_at, queued_at, created_at, updated_at) VALUES (?, ?, ?, 'QUEUED', 'MALE',"
                + " '한국대', '컴퓨터공학과', '{}'::jsonb, 0, now(), now(), now(), now()) RETURNING id", userId, cohortId, trackId);
    }

    private Room joinableCohortRoom() {
        LocalDateTime starts = LocalDateTime.now().plusHours(1);
        return roomWriter.save(Room.createScheduled(starts, starts.plusDays(2), starts.plusDays(2).plusHours(1),
                starts.plusDays(2).plusHours(2), starts.plusDays(3), 3, RoomComposition.of(cohortId, "서울 6기 1조", 3, 3)));
    }

    @Test
    @DisplayName("Hibernate 스키마 위에 up 을 다시 돌려도 안전하고, Hibernate 가 붙인 enum CHECK 까지 걷혀 CHECK 가 하나도 없다")
    void givenHibernateSchema_whenUpTwice_thenVerifiesWithoutCheckConstraints() throws Exception {
        jdbcTemplate.execute(sql("513-room-cohort-capacity-up"));
        jdbcTemplate.execute(sql("513-room-cohort-capacity-verify"));

        assertThat(count("SELECT count(*) FROM pg_constraint WHERE conrelid = 'room_members'::regclass"
                + " AND conname = 'room_members_entry_path_check'")).isZero();
        assertThat(count("SELECT count(*) FROM pg_constraint WHERE contype = 'c'"
                + " AND conrelid IN ('rooms'::regclass, 'room_members'::regclass) AND conname LIKE 'ck\\_%'")).isZero();
    }

    @Test
    @DisplayName("칸이 없던 이전 스키마의 기존 멤버는 up 뒤 모두 INVITE 이고 신청 id 가 없다")
    void givenLegacyMembers_whenUp_thenBackfilledAsInvite() throws Exception {
        executeOutsidePool(sql("513-room-cohort-capacity-down"));
        long roomId = count("INSERT INTO rooms (status, scheduled_date, total_days, created_at, updated_at)"
                + " VALUES ('PENDING', current_date, 3, now(), now()) RETURNING id");
        for (long userId = 1; userId <= 3; userId++) {
            jdbcTemplate.update("INSERT INTO room_members (room_id, user_id, joined_at, created_at, updated_at)"
                    + " VALUES (?, ?, now(), now(), now())", roomId, userId);
        }

        executeOutsidePool(sql("513-room-cohort-capacity-up"));
        executeOutsidePool(sql("513-room-cohort-capacity-verify"));

        assertThat(count("SELECT count(*) FROM room_members WHERE entry_path = 'INVITE' AND application_id IS NULL"))
                .isEqualTo(3);
        assertThat(roomReader.findById(roomId).orElseThrow().composition()).isEqualTo(RoomComposition.NONE);
    }

    @Test
    @DisplayName("DB 는 참조(없는 신청·기수)와 같은 신청 중복만 막고, 값 규칙은 도메인에 맡긴다")
    void givenInvalidRows_whenInsert_thenOnlyReferencesAndDuplicatesRejected() {
        Room room = joinableCohortRoom();
        long applicationId = application(10L);
        String member = "INSERT INTO room_members (room_id, user_id, joined_at, created_at, updated_at, entry_path,"
                + " application_id) VALUES (" + room.id() + ", %d, now(), now(), now(), '%s', %s)";

        assertThatThrownBy(() -> jdbcTemplate.execute(member.formatted(10, "APPLICATION", 99999)))
                .hasMessageContaining("fk_room_members_application");
        jdbcTemplate.execute(member.formatted(10, "APPLICATION", applicationId));
        assertThatThrownBy(() -> jdbcTemplate.execute(member.formatted(11, "APPLICATION", applicationId)))
                .hasMessageContaining("ux_room_members_application");
        // 지운(soft-delete) 멤버는 자리를 차지하지 않는다 — 배정 후 취소(#517) 뒤 다시 넣을 수 있게.
        jdbcTemplate.execute("UPDATE room_members SET deleted_at = now() WHERE application_id = " + applicationId);
        jdbcTemplate.execute(member.formatted(11, "APPLICATION", applicationId));
        assertThatThrownBy(() -> jdbcTemplate.execute("UPDATE rooms SET cohort_id = 99999 WHERE id = " + room.id()))
                .hasMessageContaining("fk_rooms_cohort");

        // CHECK 가 없으므로 정원 0 은 DB 가 받는다 — 막는 곳은 RoomComposition(도메인)이다.
        jdbcTemplate.execute("UPDATE rooms SET male_capacity = 0 WHERE id = " + room.id());
        assertThat(count("SELECT male_capacity FROM rooms WHERE id = " + room.id())).isZero();
    }

    @Test
    @DisplayName("앱 신청자 3명이 있는 방(초대 정원 3)에 6명이 동시에 초대코드로 들어오면 정확히 3명만 성공한다")
    void givenApplicationMembers_whenSixInviteJoinsRace_thenExactlyInviteCapacitySucceeds() throws Exception {
        Room room = joinableCohortRoom();
        LocalDateTime joinedAt = LocalDateTime.now().minusMinutes(1);
        for (long userId = 100L; userId < 103L; userId++) {
            roomMemberWriter.save(RoomMember.fromApplication(room.id(), userId, "MALE", null, application(userId), joinedAt));
        }
        roomInviteService.issue(room.id(), "WEB234", 3, null, "admin");

        AtomicInteger success = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();
        Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        CountDownLatch ready = new CountDownLatch(6);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (long userId = 200L; userId < 206L; userId++) {
            long user = userId;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    roomInviteService.join(user, "WEB234");
                    success.incrementAndGet();
                } catch (InviteCodeNotUsableException e) {
                    full.incrementAndGet();
                } catch (Throwable t) {
                    unexpected.add(t);
                }
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        for (var future : futures) future.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(unexpected).isEmpty();
        assertThat(success.get()).isEqualTo(3);
        assertThat(full.get()).isEqualTo(3);
        List<RoomMember> members = roomMemberReader.getByRoomId(room.id());
        assertThat(members).hasSize(6);
        assertThat(members).filteredOn(m -> m.entryPath() == RoomEntryPath.INVITE).hasSize(3);
        assertThat(count("SELECT used_count FROM room_invites WHERE code = 'WEB234'")).isEqualTo(3);
    }

    @Test
    @DisplayName("down 은 앱 신청 멤버나 편성 값이 남아 있으면 멈추고, 비운 뒤에는 칸을 걷어내며 다시 up 할 수 있다")
    void givenRows_whenDown_thenRefusesUntilCleared() throws Exception {
        Room room = joinableCohortRoom();
        roomMemberWriter.save(RoomMember.fromApplication(room.id(), 300L, "MALE", null, application(300L),
                LocalDateTime.now()));

        assertThatThrownBy(() -> executeOutsidePool(sql("513-room-cohort-capacity-down")))
                .isInstanceOf(SQLException.class).hasMessageContaining("APPLICATION members");
        jdbcTemplate.execute("DELETE FROM room_members");
        assertThatThrownBy(() -> executeOutsidePool(sql("513-room-cohort-capacity-down")))
                .isInstanceOf(SQLException.class).hasMessageContaining("cohort/name/capacity");
        jdbcTemplate.execute("UPDATE rooms SET cohort_id = NULL, name = NULL, male_capacity = NULL, female_capacity = NULL");

        executeOutsidePool(sql("513-room-cohort-capacity-down"));
        executeOutsidePool(sql("513-room-cohort-capacity-down")); // 이미 내린 뒤 다시 돌려도 멈추지 않는다
        assertThat(count("SELECT count(*) FROM information_schema.columns WHERE table_name IN ('rooms', 'room_members')"
                + " AND column_name IN ('cohort_id', 'name', 'male_capacity', 'female_capacity', 'entry_path',"
                + " 'application_id')")).isZero();

        executeOutsidePool(sql("513-room-cohort-capacity-up"));
        executeOutsidePool(sql("513-room-cohort-capacity-verify"));
    }

    @Test
    @DisplayName("verify 는 신청 유니크 조건·기본값이 어긋나거나 CHECK 가 다시 생기면 멈춘다")
    void givenDriftedSchema_whenVerify_thenFails() throws Exception {
        jdbcTemplate.execute("DROP INDEX ux_room_members_application;"
                + "CREATE UNIQUE INDEX ux_room_members_application ON room_members (application_id)");
        assertThatThrownBy(() -> executeOutsidePool(sql("513-room-cohort-capacity-verify")))
                .isInstanceOf(SQLException.class).hasMessageContaining("ux_room_members_application");

        jdbcTemplate.execute("DROP INDEX ux_room_members_application");
        jdbcTemplate.execute(sql("513-room-cohort-capacity-up"));
        jdbcTemplate.execute("ALTER TABLE room_members ALTER COLUMN entry_path DROP DEFAULT");
        assertThatThrownBy(() -> executeOutsidePool(sql("513-room-cohort-capacity-verify")))
                .isInstanceOf(SQLException.class).hasMessageContaining("default to INVITE");

        jdbcTemplate.execute(sql("513-room-cohort-capacity-up"));
        jdbcTemplate.execute("ALTER TABLE rooms ADD CONSTRAINT stray_capacity_check CHECK (male_capacity >= 1)");
        assertThatThrownBy(() -> executeOutsidePool(sql("513-room-cohort-capacity-verify")))
                .isInstanceOf(SQLException.class).hasMessageContaining("stray_capacity_check");
        // up 을 다시 돌리면 걷어낸다.
        jdbcTemplate.execute(sql("513-room-cohort-capacity-up"));
        jdbcTemplate.execute(sql("513-room-cohort-capacity-verify"));
    }

    @Test
    @DisplayName("verify 는 같은 이름이라도 다른 칸을 가리키거나 검증 전(NOT VALID)인 FK 면 멈춘다")
    void givenSameNamedWrongForeignKey_whenVerify_thenFails() throws Exception {
        String[] drifts = {
                "ALTER TABLE room_members ADD CONSTRAINT fk_room_members_application"
                        + " FOREIGN KEY (user_id) REFERENCES cohort_applications (id)",
                "ALTER TABLE room_members ADD CONSTRAINT fk_room_members_application"
                        + " FOREIGN KEY (application_id) REFERENCES cohort_applications (id) NOT VALID",
                "ALTER TABLE room_members ADD CONSTRAINT fk_room_members_application"
                        + " FOREIGN KEY (application_id) REFERENCES cohort_applications (id) ON DELETE CASCADE"};
        for (String drift : drifts) {
            jdbcTemplate.execute("ALTER TABLE room_members DROP CONSTRAINT fk_room_members_application");
            jdbcTemplate.execute(drift);
            jdbcTemplate.execute(sql("513-room-cohort-capacity-up")); // 이름이 있으니 up 은 건너뛴다
            assertThatThrownBy(() -> executeOutsidePool(sql("513-room-cohort-capacity-verify"))).as(drift)
                    .isInstanceOf(SQLException.class).hasMessageContaining("fk_room_members_application");
        }
    }

    @Test
    @DisplayName("verify 는 직접 SQL 로 도메인 규칙을 어긴 살아 있는 행(모르는 경로·짝 어긋남·정원 0)이 있으면 멈추고, 지운 행은 보지 않는다")
    void givenRowsBreakingDomainRules_whenVerify_thenFails() throws Exception {
        Room room = joinableCohortRoom();
        String member = "INSERT INTO room_members (room_id, user_id, joined_at, created_at, updated_at, entry_path,"
                + " application_id, deleted_at) VALUES (" + room.id() + ", %d, now(), now(), now(), '%s', %s, %s)";
        String[] pollutions = {
                member.formatted(20, "ADMIN", "NULL", "NULL"),
                member.formatted(21, "APPLICATION", "NULL", "NULL"),
                member.formatted(22, "INVITE", application(22L), "NULL"),
                "UPDATE rooms SET female_capacity = 0 WHERE id = " + room.id()};
        for (String pollution : pollutions) {
            jdbcTemplate.execute(pollution);
            assertThatThrownBy(() -> executeOutsidePool(sql("513-room-cohort-capacity-verify"))).as(pollution)
                    .isInstanceOf(SQLException.class).hasMessageContaining("domain rules");
            jdbcTemplate.execute("DELETE FROM room_members WHERE user_id >= 20");
            jdbcTemplate.execute("UPDATE rooms SET female_capacity = 3 WHERE id = " + room.id());
        }

        // 지운(soft-delete) 행은 조회되지 않으므로 감사하지 않는다.
        jdbcTemplate.execute(member.formatted(23, "APPLICATION", "NULL", "now()"));
        executeOutsidePool(sql("513-room-cohort-capacity-verify"));
    }
}
