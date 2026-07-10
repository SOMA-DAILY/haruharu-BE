package org.son.haruharu.admin.domain.dashboard;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
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
}
