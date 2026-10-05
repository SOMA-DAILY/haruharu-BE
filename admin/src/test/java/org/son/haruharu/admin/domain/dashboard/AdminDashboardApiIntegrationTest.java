package org.son.haruharu.admin.domain.dashboard;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.admin.domain.account.implement.AdminRole;
import org.son.haruharu.admin.security.AdminJwtProvider;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminDashboardApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AdminJwtProvider adminJwtProvider;
    @LocalServerPort int port;

    @PersistenceContext
    EntityManager em;

    private static UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER")));
    }

    @Test
    @DisplayName("요약 조회 시 진행중 방(ACTIVE만)·오늘 가입 수가 집계된다")
    void summary_returnsAggregates() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        // 진행중 방 2개(ACTIVE) + 종료 방 1개(ENDED, 제외돼야)
        em.persist(RoomEntity.from(Room.create(LocalDate.now(), 3).started(now)));
        em.persist(RoomEntity.from(Room.create(LocalDate.now(), 3).started(now)));
        em.persist(RoomEntity.from(Room.create(LocalDate.now(), 3).started(now.minusDays(3)).ended(now)));
        // 오늘 가입 유저 2명(created_at 감사 자동 = now)
        em.persist(UserEntity.from(new User(null, UserStatus.ACTIVE, null)));
        em.persist(UserEntity.from(new User(null, UserStatus.ACTIVE, null)));
        em.flush();

        mockMvc.perform(get("/api/v1/admin/dashboard/summary").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.active_rooms.total").value(2))
                .andExpect(jsonPath("$.data.today_signups").value(2))
                .andExpect(jsonPath("$.data.matching_queue").doesNotExist());
    }

    @Test
    @DisplayName("인증 없이 대시보드 접근 시 401")
    void summary_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("metrics는 기간별 가입 추이를 반환하고 자동매칭 지표는 포함하지 않는다")
    void metrics_returnsSignupTrendsWithoutAutoMatching() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate d1 = today.minusDays(1);
        // d1 가입 1명(created_at 백데이트)
        persistUserSignedUpAt(d1.atTime(10, 0));
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/v1/admin/dashboard/metrics")
                        .param("from", d1.toString()).param("to", today.toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range.from").value(d1.toString()))
                .andExpect(jsonPath("$.data.range.to").value(today.toString()))
                .andExpect(jsonPath("$.data.signups.length()").value(2))
                .andExpect(jsonPath("$.data.signups[0].date").value(d1.toString()))
                .andExpect(jsonPath("$.data.signups[0].count").value(1))
                .andExpect(jsonPath("$.data.signups[1].count").value(0))
                .andExpect(jsonPath("$.data.matching").doesNotExist());
    }

    @Test
    @DisplayName("from이 to보다 이후면 400")
    void metrics_fromAfterTo_returns400() throws Exception {
        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/api/v1/admin/dashboard/metrics")
                        .param("from", today.toString()).param("to", today.minusDays(3).toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ADMIN_DASHBOARD_INVALID_RANGE"));
    }

    @Test
    @DisplayName("인증 없이 metrics 접근 시 401")
    void metrics_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("실제 HTTP 대시보드 응답에서도 자동매칭 통계가 제거된다")
    void givenAdminToken_whenHttpGetsDashboard_thenAutoMatchingMetricsAreAbsent() throws Exception {
        String authorization = "Bearer " + adminJwtProvider.generateAccessToken(1L, AdminRole.SUPER);
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (String endpoint : List.of("summary", "metrics")) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/api/v1/admin/dashboard/" + endpoint))
                        .header("Authorization", authorization).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).doesNotContain("\"matching\"", "\"matching_queue\"");
                assertThat(response.body()).contains(endpoint.equals("summary") ? "\"active_rooms\"" : "\"signups\"");
            }
        }
    }

    /** created_at은 @CreatedDate 자동 세팅이라, persist 후 네이티브 UPDATE로 특정 과거일자로 백데이트한다. */
    private void persistUserSignedUpAt(LocalDateTime signedUpAt) {
        UserEntity entity = UserEntity.from(new User(null, UserStatus.ACTIVE, null));
        em.persist(entity);
        em.flush();
        em.createNativeQuery("update users set created_at = :ts where id = :id")
                .setParameter("ts", signedUpAt)
                .setParameter("id", entity.toDomain().id())
                .executeUpdate();
    }
}
