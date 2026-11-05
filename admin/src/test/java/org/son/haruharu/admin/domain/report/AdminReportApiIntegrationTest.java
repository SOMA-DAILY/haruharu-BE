package org.son.haruharu.admin.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.auth.implement.RefreshToken;
import org.son.haruharu.domain.auth.storage.RefreshTokenEntity;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.report.implement.Report;
import org.son.haruharu.domain.report.implement.ReportReason;
import org.son.haruharu.domain.report.storage.ReportEntity;
import org.son.haruharu.domain.room.implement.RoomEntryPath;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminReportApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    @PersistenceContext
    EntityManager em;

    private static UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private Long user(String nickname) {
        UserEntity user = UserEntity.from(new User(null, UserStatus.ACTIVE, null));
        em.persist(user);
        Long id = user.toDomain().id();
        em.persist(UserProfileEntity.from(new UserProfile(null, id, "서울", "직장인", 170, "INFP", nickname, null, null, null)));
        return id;
    }

    private Long report(Long reporter, Long reported) {
        ReportEntity entity = ReportEntity.from(Report.received(reporter, reported, ReportReason.ABUSIVE_LANGUAGE, "욕설", LocalDateTime.now()));
        em.persist(entity);
        em.flush();
        return entity.toDomain().id();
    }

    private UserStatus statusOf(Long userId) {
        em.clear();
        return em.find(UserEntity.class, userId).toDomain().status();
    }

    @Test
    @DisplayName("목록은 최신순이고 신고자·피신고자 닉네임·상태·참여 방 id 가 담긴다")
    void reports_list() throws Exception {
        Long a = user("신고자");
        Long b = user("피신고자");
        em.persist(RoomMemberEntity.from(new RoomMember(null, 77L, b, "MALE", "가명", LocalDate.of(2000, 1, 1),
                LocalDateTime.now(), RoomEntryPath.INVITE, null)));
        em.persist(RoomMemberEntity.from(new RoomMember(null, 77L, a, "FEMALE", "가명2", LocalDate.of(2000, 1, 1),
                LocalDateTime.now(), RoomEntryPath.INVITE, null)));
        em.persist(RoomMemberEntity.from(new RoomMember(null, 88L, b, "MALE", "가명3", LocalDate.of(2000, 1, 1),
                LocalDateTime.now(), RoomEntryPath.INVITE, null)));
        report(a, b);
        Long recent = report(b, a);

        mockMvc.perform(get("/api/v1/admin/reports").param("status", "RECEIVED").with(authentication(auth("CS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].report_id").value(recent))
                .andExpect(jsonPath("$.data.content[1].reporter.nickname").value("신고자"))
                .andExpect(jsonPath("$.data.content[1].reported.nickname").value("피신고자"))
                .andExpect(jsonPath("$.data.content[1].reported.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.content[1].reported.room_ids[0]").value(88))
                .andExpect(jsonPath("$.data.content[1].room_id").value(77))
                .andExpect(jsonPath("$.data.content[1].resolved_at").doesNotExist())
                .andExpect(jsonPath("$.data.page.total_elements").value(2));
    }

    @Test
    @DisplayName("승인하면 신고는 SANCTIONED, 피신고자는 BANNED 가 되고 리프레시 토큰이 사라진다. 재처리는 409")
    void approve_bansReportedUser() throws Exception {
        Long a = user("신고자");
        Long b = user("피신고자");
        em.persist(RefreshTokenEntity.from(new RefreshToken(null, b, "hash-b", LocalDateTime.now().plusDays(1), null)));
        Long id = report(a, b);

        mockMvc.perform(post("/api/v1/admin/reports/{id}/approve", id).with(authentication(auth("OPERATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SANCTIONED"))
                .andExpect(jsonPath("$.data.resolved_by_admin_id").value(1))
                .andExpect(jsonPath("$.data.resolved_at").isNotEmpty())
                .andExpect(jsonPath("$.data.reported.status").value("BANNED"))
                .andExpect(jsonPath("$.data.reported_user_report_count").value(1));

        assertThat(statusOf(b)).isEqualTo(UserStatus.BANNED);
        assertThat(em.createQuery("select count(t) from RefreshTokenEntity t where t.userId = :u", Long.class)
                .setParameter("u", b).getSingleResult()).isZero();

        mockMvc.perform(post("/api/v1/admin/reports/{id}/approve", id).with(authentication(auth("SUPER"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_ALREADY_RESOLVED"));
    }

    @Test
    @DisplayName("기각하면 REJECTED 가 되고 피신고자 상태는 그대로다")
    void reject_keepsUser() throws Exception {
        Long a = user("신고자");
        Long b = user("피신고자");
        Long id = report(a, b);

        mockMvc.perform(post("/api/v1/admin/reports/{id}/reject", id).with(authentication(auth("SUPER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        assertThat(statusOf(b)).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("CS 는 승인·기각을 못 하고(403) 없는 신고는 404")
    void approve_forbiddenForCs_and_notFound() throws Exception {
        Long id = report(user("a"), user("b"));
        mockMvc.perform(post("/api/v1/admin/reports/{id}/approve", id).with(authentication(auth("CS"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/reports/{id}/reject", id).with(authentication(auth("CS"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/reports/{id}", 999999).with(authentication(auth("CS"))))
                .andExpect(status().isNotFound());
    }
}
