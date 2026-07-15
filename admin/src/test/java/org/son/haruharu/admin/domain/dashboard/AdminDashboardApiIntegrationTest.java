package org.son.haruharu.admin.domain.dashboard;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.son.haruharu.domain.matching.storage.MatchingQueueEntity;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminDashboardApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    @PersistenceContext
    EntityManager em;

    private static UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER")));
    }

    @Test
    @DisplayName("요약 조회 시 진행중 방(ACTIVE만)·매칭 대기(QUEUED 성비)·오늘 가입 수가 집계된다")
    void summary_returnsAggregates() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        // 진행중 방 2개(ACTIVE) + 종료 방 1개(ENDED, 제외돼야)
        em.persist(RoomEntity.from(Room.create(LocalDate.now(), 3).started(now)));
        em.persist(RoomEntity.from(Room.create(LocalDate.now(), 3).started(now)));
        em.persist(RoomEntity.from(Room.create(LocalDate.now(), 3).started(now.minusDays(3)).ended(now)));
        // 매칭 대기: 여1·남1 + 취소1(QUEUED 아님, 제외)
        em.persist(MatchingQueueEntity.from(MatchingQueue.join(10L, "FEMALE", LocalDate.of(2000, 1, 1), now)));
        em.persist(MatchingQueueEntity.from(MatchingQueue.join(11L, "MALE", LocalDate.of(2000, 1, 1), now)));
        em.persist(MatchingQueueEntity.from(MatchingQueue.join(12L, "FEMALE", LocalDate.of(2000, 1, 1), now).cancelled()));
        // 오늘 가입 유저 2명(created_at 감사 자동 = now)
        em.persist(UserEntity.from(new User(null, UserStatus.ACTIVE, null)));
        em.persist(UserEntity.from(new User(null, UserStatus.ACTIVE, null)));
        em.flush();

        mockMvc.perform(get("/api/v1/admin/dashboard/summary").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.active_rooms.total").value(2))
                .andExpect(jsonPath("$.data.matching_queue.total").value(2))
                .andExpect(jsonPath("$.data.matching_queue.female").value(1))
                .andExpect(jsonPath("$.data.matching_queue.male").value(1))
                .andExpect(jsonPath("$.data.today_signups").value(2));
    }

    @Test
    @DisplayName("인증 없이 대시보드 접근 시 401")
    void summary_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("metrics는 기간별 가입·매칭 추이와 성공률(CANCELLED 제외)을 반환한다")
    void metrics_returnsTrendsWithSuccessRate() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate d1 = today.minusDays(1);
        LocalDateTime queuedAt = d1.atTime(12, 0);

        // d1 매칭 큐: MATCHED 2, EXPIRED 1, CANCELLED 1 → 성공률 2/(2+1)=0.6667, applications=4
        persistQueue(20L, "FEMALE", queuedAt, MatchingQueueStatus.MATCHED);
        persistQueue(21L, "MALE", queuedAt, MatchingQueueStatus.MATCHED);
        persistQueue(22L, "FEMALE", queuedAt, MatchingQueueStatus.EXPIRED);
        persistQueue(23L, "MALE", queuedAt, MatchingQueueStatus.CANCELLED);
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
                .andExpect(jsonPath("$.data.matching.daily[0].applications").value(4))
                .andExpect(jsonPath("$.data.matching.daily[0].matched").value(2))
                .andExpect(jsonPath("$.data.matching.daily[0].expired").value(1))
                .andExpect(jsonPath("$.data.matching.daily[0].success_rate").value(0.6667))
                .andExpect(jsonPath("$.data.matching.overall.success_rate").value(0.6667));
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

    private void persistQueue(Long userId, String gender, LocalDateTime queuedAt, MatchingQueueStatus status) {
        LocalDateTime matchedAt = status == MatchingQueueStatus.MATCHED ? queuedAt.plusHours(1) : null;
        em.persist(MatchingQueueEntity.from(new MatchingQueue(
                null, userId, gender, LocalDate.of(2000, 1, 1), status, queuedAt, matchedAt)));
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
