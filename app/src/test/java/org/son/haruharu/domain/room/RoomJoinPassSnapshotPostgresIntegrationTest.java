package org.son.haruharu.domain.room;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.identity.implement.UserIdentity;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.son.haruharu.domain.room.business.RoomInviteService;
import org.son.haruharu.domain.room.business.RoomJoinService;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
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

/**
 * #514 PASS 성별·생년 스냅샷을 실제 PostgreSQL 에서 확인한다.
 * H2 는 JVM 이 UTC 로 뜬 뒤 TimeZoneConfig 가 기본 시간대를 바꾸면 LocalDate 를 하루씩 밀려 저장하므로(CI)
 * 생년 값 자체는 여기서 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoomJoinPassSnapshotPostgresIntegrationTest {

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

    @Autowired RoomJoinService roomJoinService;
    @Autowired RoomInviteService roomInviteService;
    @Autowired RoomWriter roomWriter;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired UserIdentityWriter userIdentityWriter;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("PASS 계정이 초대코드로 입장하면 room_members 에 PASS 성별·생년이 그대로 저장된다")
    void givenPass_whenJoin_thenRoomMemberStoresPassGenderAndBirthday() {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        userIdentityWriter.save(new UserIdentity(null, userId, "ci-" + userId, "di-" + userId, "01099998888",
                LocalDate.of(2001, 3, 4), "FEMALE", "홍길동", "SKT", false, LocalDateTime.now()));
        LocalDateTime starts = LocalDateTime.now().plusHours(1);
        Room room = roomWriter.save(Room.createScheduled(starts, starts.plusDays(2), starts.plusDays(2).plusHours(1),
                starts.plusDays(2).plusHours(2), starts.plusDays(3), 3));
        String code = roomInviteService.issueFor(room.id(), 6, null, "admin").code();

        roomJoinService.join(userId, code);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT gender, birthday::text AS birthday FROM room_members WHERE room_id = ? AND user_id = ?",
                room.id(), userId);
        assertThat(row.get("gender")).isEqualTo("FEMALE");
        assertThat(row.get("birthday")).isEqualTo("2001-03-04");
    }
}
