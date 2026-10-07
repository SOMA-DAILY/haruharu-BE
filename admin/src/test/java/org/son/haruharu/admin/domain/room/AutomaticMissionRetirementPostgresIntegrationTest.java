package org.son.haruharu.admin.domain.room;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AutomaticMissionRetirementPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCopyFileToContainer(MountableFile.forClasspathResource("legacy-room-missions.sql"),
                    "/docker-entrypoint-initdb.d/legacy-room-missions.sql");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("기존 미션 설정·미래 슬롯이 있어도 신규 방은 미션을 만들거나 과거 행을 변경하지 않는다")
    void givenLegacyMissionData_whenCreatingRoom_thenPreservesHistoryWithoutNewMissions(boolean legacyInput)
            throws Exception {
        var settings = jdbc.queryForList("SELECT * FROM room_mission_settings ORDER BY id");
        var slots = jdbc.queryForList("SELECT * FROM room_mission_slots ORDER BY id");
        assertThat(settings).hasSize(1);
        assertThat(slots).hasSize(1);
        String body = """
                {"starts_at":"2030-01-01T19:00:00","final_selection_opens_at":"2030-01-03T18:00:00",
                 "final_selection_closes_at":"2030-01-03T21:00:00","result_at":"2030-01-03T22:00:00",
                 "ends_at":"2030-01-04T19:00:00","total_days":3,"max_uses":6,
                 "missions":[{"type":"ANNOUNCEMENT","opens_at":"2030-01-01T19:00:00","details":{"content":"안내"}}]%s}
                """.formatted(legacyInput ? ",\"mission_enabled\":true,\"mission_slots\":[]" : "");
        var auth = new UsernamePasswordAuthenticationToken(1L, null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));

        String response = mvc.perform(post("/api/v1/admin/rooms").with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        Long roomId = com.jayway.jsonpath.JsonPath.parse(response).read("$.data.room_id", Long.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM room_invites WHERE room_id = ?", Long.class, roomId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM room_mission_instances WHERE room_id = ?", Long.class, roomId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForList("SELECT * FROM room_mission_settings ORDER BY id")).isEqualTo(settings);
        assertThat(jdbc.queryForList("SELECT * FROM room_mission_slots ORDER BY id")).isEqualTo(slots);

        java.nio.file.Path root = java.nio.file.Path.of("").toAbsolutePath();
        while (root != null && !java.nio.file.Files.isDirectory(root.resolve("scripts/sql"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IllegalStateException("scripts/sql 디렉터리를 찾지 못했다");
        }
        jdbc.execute(java.nio.file.Files.readString(root.resolve("scripts/sql/482-mission-retirement-verify.sql")));
        assertThat(jdbc.queryForList("SELECT * FROM room_mission_settings ORDER BY id")).isEqualTo(settings);
        assertThat(jdbc.queryForList("SELECT * FROM room_mission_slots ORDER BY id")).isEqualTo(slots);
    }
}
