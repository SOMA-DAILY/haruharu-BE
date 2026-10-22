package org.son.haruharu.admin.domain.room;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.son.haruharu.admin.domain.room.business.*;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminRoomOrganizationPostgresIntegrationTest extends AdminRoomOrganizationApiIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add(
                "spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AdminRoomOrganizationService organization;
    @Autowired AdminRoomService rooms;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void applyRealMigration() throws Exception {
        jdbc.execute("DROP TABLE admin_room_organizations");
        jdbc.execute("DROP TABLE admin_room_groups");
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("scripts/sql")))
            root = root.getParent();
        String sql = Files.readString(root.resolve("scripts/sql/536-admin-room-groups-up.sql"));
        jdbc.execute(sql);
        jdbc.execute(sql);
        jdbc.execute(Files.readString(root.resolve("scripts/sql/536-admin-room-groups-verify.sql")));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentPartialAssignmentsPreserveBothChanges() throws Exception {
        var tx = new TransactionTemplate(transactions);
        long roomId =
                tx.execute(
                        s -> {
                            var e = RoomEntity.from(Room.create(LocalDate.now(), 3));
                            em.persist(e);
                            em.flush();
                            return e.toDomain().id();
                        });
        long groupId = organization.createGroup("앱 concurrent").id();
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first =
                    executor.submit(
                            () -> {
                                ready.countDown();
                                start.await();
                                organization.assign(List.of(roomId), groupId, false, null);
                                return true;
                            });
            var second =
                    executor.submit(
                            () -> {
                                ready.countDown();
                                start.await();
                                organization.assign(List.of(roomId), null, false, true);
                                return true;
                            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            var result = rooms.getRoom(roomId);
            assertThat(result.groupId()).isEqualTo(groupId);
            assertThat(result.isTest()).isTrue();
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            jdbc.update("DELETE FROM admin_room_organizations WHERE room_id=?", roomId);
            jdbc.update("DELETE FROM admin_room_groups WHERE id=?", groupId);
            jdbc.update("DELETE FROM rooms WHERE id=?", roomId);
        }
    }

    @Test
    void migrationEnforcesRoomReference() {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO admin_room_organizations(room_id,is_test)"
                                            + " VALUES(999999999,false)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
