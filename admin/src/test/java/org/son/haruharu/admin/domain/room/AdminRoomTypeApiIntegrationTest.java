package org.son.haruharu.admin.domain.room;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminRoomTypeApiIntegrationTest {

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

    @Autowired MockMvc mockMvc;

    private static UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(1L, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private String body(LocalDateTime start, Boolean isTest) {
        return """
                {
                  "starts_at": "%s",
                  "final_selection_opens_at": "%s",
                  "final_selection_closes_at": "%s",
                  "result_at": "%s",
                  "ends_at": "%s",
                  "total_days": 3,
                  "max_uses": 6%s
                }
                """.formatted(start, start.plusHours(60), start.plusHours(63),
                start.plusHours(64), start.plusHours(72),
                isTest == null ? "" : ",\n  \"is_test\": " + isTest);
    }

    private Long create(LocalDateTime start, Boolean isTest) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON).content(body(start, isTest))
                        .with(authentication(auth("OPERATOR"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(response).read("$.data.room_id", Long.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("운영·테스트 선택값은 생성 결과와 방 상세에 저장된 값으로 반환된다")
    void givenRoomType_whenCreatingRoom_thenPersistsAndReturnsType(boolean isTest) throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(30).withNano(0);
        String response = mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON).content(body(start, isTest))
                        .with(authentication(auth("OPERATOR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.is_test").value(isTest))
                .andReturn().getResponse().getContentAsString();
        Long id = JsonPath.parse(response).read("$.data.room_id", Long.class);
        mockMvc.perform(get("/api/v1/admin/rooms/{id}", id).with(authentication(auth("CS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_test").value(isTest));
    }

    @Test
    @DisplayName("방 구분 생성·변경은 develop의 방 이름·성별 정원을 함께 보존한다")
    void givenRoomComposition_whenCreatingAndChangingType_thenPreservesComposition() throws Exception {
        String request = body(LocalDateTime.now().plusDays(30).withNano(0), true)
                .replace("\"max_uses\": 6", "\"max_uses\": 6, \"name\": \"운영 구분 검증\", \"male_capacity\": 3, \"female_capacity\": 2");
        String response = mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON).content(request)
                        .with(authentication(auth("OPERATOR"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = JsonPath.parse(response).read("$.data.room_id", Long.class);
        for (boolean isTest : new boolean[]{true, false}) {
            if (!isTest) {
                mockMvc.perform(patch("/api/v1/admin/rooms/{id}/type", id)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"is_test\":false}")
                                .with(authentication(auth("OPERATOR"))))
                        .andExpect(status().isOk());
            }
            mockMvc.perform(get("/api/v1/admin/rooms/{id}", id).with(authentication(auth("CS"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.is_test").value(isTest))
                    .andExpect(jsonPath("$.data.name").value("운영 구분 검증"))
                    .andExpect(jsonPath("$.data.male_capacity").value(3))
                    .andExpect(jsonPath("$.data.female_capacity").value(2));
        }
    }

    @Test
    @DisplayName("구분을 생략한 기존 클라이언트는 운영방을 생성한다")
    void givenMissingType_whenCreatingRoom_thenDefaultsToOperational() throws Exception {
        Long id = create(LocalDateTime.now().plusDays(30).withNano(0), null);
        mockMvc.perform(get("/api/v1/admin/rooms/{id}", id).with(authentication(auth("CS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_test").value(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("구분·상태·예정일 필터는 모든 페이지의 목록과 전체 건수에 동일하게 적용된다")
    void givenMixedRooms_whenFilteringAndPaging_thenReturnsMatchingRowsAndCount(boolean isTest) throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(30).withNano(0);
        for (int i = 0; i < 3; i++) {
            create(start, false);
            create(start, true);
        }
        create(start.plusDays(1), isTest);
        for (int page = 0; page < 2; page++) {
            mockMvc.perform(get("/api/v1/admin/rooms")
                            .param("isTest", Boolean.toString(isTest))
                            .param("status", "PENDING").param("scheduledDate", start.toLocalDate().toString())
                            .param("page", Integer.toString(page)).param("size", "2")
                            .with(authentication(auth("CS"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(page == 0 ? 2 : 1))
                    .andExpect(jsonPath("$.data.content[*].is_test").value(org.hamcrest.Matchers.everyItem(
                            org.hamcrest.Matchers.is(isTest))))
                    .andExpect(jsonPath("$.data.page.total_elements").value(3))
                    .andExpect(jsonPath("$.data.page.total_pages").value(2));
        }
        mockMvc.perform(get("/api/v1/admin/rooms").param("scheduledDate", start.toLocalDate().toString())
                        .with(authentication(auth("CS"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total_elements").value(6));
        mockMvc.perform(get("/api/v1/admin/rooms").param("isTest", Boolean.toString(isTest))
                        .param("scheduledDate", start.toLocalDate().toString()).param("status", "ENDED")
                        .with(authentication(auth("CS"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.page.total_elements").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERATOR", "SUPER"})
    @DisplayName("운영진은 방 구분을 바꿀 수 있고 취소 이후에도 변경한 구분이 유지된다")
    void givenWriter_whenChangingTypeAndCancelling_thenPreservesType(String role) throws Exception {
        Long id = create(LocalDateTime.now().plusDays(30).withNano(0), false);
        mockMvc.perform(patch("/api/v1/admin/rooms/{id}/type", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"is_test\":true}")
                        .with(authentication(auth(role))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.room_id").value(id))
                .andExpect(jsonPath("$.data.is_test").value(true));
        mockMvc.perform(post("/api/v1/admin/rooms/{id}/cancel", id).with(authentication(auth(role))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/rooms/{id}", id).with(authentication(auth("CS"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.is_test").value(true));
        mockMvc.perform(patch("/api/v1/admin/rooms/{id}/type", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"is_test\":false}")
                        .with(authentication(auth(role))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.is_test").value(false));
    }

    @Test
    @DisplayName("CS와 미인증 요청은 방 구분을 변경할 수 없다")
    void givenRestrictedUser_whenChangingType_thenRejected() throws Exception {
        Long id = create(LocalDateTime.now().plusDays(30).withNano(0), false);
        mockMvc.perform(patch("/api/v1/admin/rooms/{id}/type", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"is_test\":true}")
                        .with(authentication(auth("CS"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/rooms/{id}/type", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"is_test\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("없는 방은 404이고 변경 값 누락·null과 잘못된 필터는 400이다")
    void givenMissingRoomOrInvalidType_whenRequesting_thenRejectsInvalidRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/rooms/{id}/type", 999999999L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"is_test\":true}")
                        .with(authentication(auth("OPERATOR"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ADMIN_ROOM_NOT_FOUND"));
        for (String invalid : List.of("{}", "{\"is_test\":null}")) {
            mockMvc.perform(patch("/api/v1/admin/rooms/{id}/type", 1L)
                            .contentType(MediaType.APPLICATION_JSON).content(invalid)
                            .with(authentication(auth("OPERATOR"))))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/admin/rooms").param("isTest", "invalid")
                        .with(authentication(auth("CS"))))
                .andExpect(status().isBadRequest());
    }
}
