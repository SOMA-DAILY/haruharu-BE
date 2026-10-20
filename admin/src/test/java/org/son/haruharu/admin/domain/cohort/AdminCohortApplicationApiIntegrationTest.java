package org.son.haruharu.admin.domain.cohort;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.cohort.implement.ApplicantGender;
import org.son.haruharu.domain.cohort.implement.ApplicationReviewReason;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.son.haruharu.domain.cohort.storage.CohortApplicationEntity;
import org.son.haruharu.domain.cohort.storage.CohortEntity;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminCohortApplicationApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @PersistenceContext EntityManager em;

    private static final LocalDateTime NOW = LocalDateTime.now().withNano(0);

    private ResultActions send(MockHttpServletRequestBuilder request, String role) throws Exception {
        return mockMvc.perform(request.with(authentication(new UsernamePasswordAuthenticationToken(7L, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))))));
    }

    private int sequence = 6;

    private long cohort() {
        CohortEntity entity = CohortEntity.from(new Cohort(null, 1L, sequence++, CohortStatus.OPEN, NOW.minusDays(1),
                NOW.plusDays(3), NOW.plusDays(4), "10월 마지막 주", 6, 6, null, "admin:1", NOW, NOW));
        em.persist(entity);
        return entity.toDomain().id();
    }

    private long application(long cohortId, long userId, CohortApplicationStatus status, ApplicantGender gender,
                             String passGender, int priority, LocalDateTime queuedAt, Long roomId) {
        CohortApplicationEntity entity = CohortApplicationEntity.from(new CohortApplication(null, userId, cohortId, 1L,
                status, gender, passGender, LocalDate.of(2003, 5, 1), "한국대", "컴퓨터공학과", null, null, null, null,
                null, priority, NOW.minusHours(5), queuedAt,
                status == CohortApplicationStatus.NEEDS_REVIEW ? ApplicationReviewReason.GENDER_MISMATCH : null,
                roomId, null, null, null, null, null, null, NOW, NOW));
        em.persist(entity);
        return entity.toDomain().id();
    }

    private void person(long userId, String name, String phone) {
        em.createNativeQuery("insert into user_identities (id, user_id, user_name, created_at, updated_at) "
                + "values (" + userId + ", " + userId + ", '" + name + "', now(), now())").executeUpdate();
        em.persist(UserProfileEntity.from(new UserProfile(null, userId, null, null, null, null, "닉" + userId, null,
                null, null, null, null, null, phone)));
    }

    @Test
    @DisplayName("신청자 표는 우선순위 → 대기 순으로 PASS 이름·나이·번호·지난 방을 함께 주고 상태·성별로 거른다")
    void givenApplications_whenList_thenSortedFilteredAndJoined() throws Exception {
        long cohortId = cohort();
        person(901L, "김하루", "01011112222");
        person(902L, "이하루", "01033334444");
        application(cohortId, 901L, CohortApplicationStatus.QUEUED, ApplicantGender.FEMALE, "FEMALE", 0, NOW.minusHours(3), null);
        application(cohortId, 902L, CohortApplicationStatus.QUEUED, ApplicantGender.MALE, "MALE", 1, NOW.minusHours(1), null);
        application(cohortId, 903L, CohortApplicationStatus.IDENTITY_PENDING, null, null, 0, null, null);
        application(cohort(), 904L, CohortApplicationStatus.QUEUED, ApplicantGender.FEMALE, "FEMALE", 0, NOW, null);
        em.persist(RoomMemberEntity.from(RoomMember.of(62L, 901L, "FEMALE", null, NOW.minusDays(30))));
        em.persist(RoomMemberEntity.from(RoomMember.of(70L, 901L, "FEMALE", null, NOW.minusDays(10))));
        em.flush();

        send(get("/api/v1/admin/cohorts/" + cohortId + "/applications"), "OPERATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.total_elements").value(3))
                .andExpect(jsonPath("$.data.content[*].user_id").value(contains(902, 901, 903)))
                .andExpect(jsonPath("$.data.content[1].name").value("김하루"))
                .andExpect(jsonPath("$.data.content[1].phone").value("01011112222"))
                .andExpect(jsonPath("$.data.content[1].age").value(NOW.getYear() - 2003))
                .andExpect(jsonPath("$.data.content[1].past_room_ids").value(contains(70, 62)))
                .andExpect(jsonPath("$.data.content[2].name").doesNotExist());

        send(get("/api/v1/admin/cohorts/" + cohortId + "/applications?status=QUEUED&gender=FEMALE"), "OPERATOR")
                .andExpect(jsonPath("$.data.page.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].user_id").value(901));
        send(get("/api/v1/admin/cohorts/" + cohortId + "/applications?page=1&size=2"), "OPERATOR")
                .andExpect(jsonPath("$.data.content[*].user_id").value(contains(903)));
        send(get("/api/v1/admin/cohorts/" + cohortId + "/applications?page=2147483647"), "OPERATOR")
                .andExpect(status().isOk());
        send(get("/api/v1/admin/cohorts/999999/applications"), "OPERATOR").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("신청자가 늘어도 목록 쿼리 수는 그대로다(N+1 없음)")
    void givenMoreApplicants_whenList_thenSameStatementCount() throws Exception {
        long cohortId = cohort();
        person(901L, "김하루", "01011112222");
        application(cohortId, 901L, CohortApplicationStatus.QUEUED, ApplicantGender.FEMALE, "FEMALE", 0, NOW, null);
        em.flush();
        long one = statementsFor(cohortId);
        for (long userId = 902L; userId < 907L; userId++) {
            person(userId, "하루" + userId, "0101111" + userId);
            application(cohortId, userId, CohortApplicationStatus.QUEUED, ApplicantGender.MALE, "MALE", 0, NOW, null);
            em.persist(RoomMemberEntity.from(RoomMember.of(60L + userId, userId, "MALE", null, NOW)));
        }
        em.flush();

        assertThat(statementsFor(cohortId)).isEqualTo(one);
    }

    private long statementsFor(long cohortId) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        send(get("/api/v1/admin/cohorts/" + cohortId + "/applications"), "OPERATOR").andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    @Test
    @DisplayName("운영진은 메모를 적고 지울 수 있고, 확인 필요 신청을 PASS 성별로 편성 대기에 올린다")
    void operator_updatesMemoAndApproves() throws Exception {
        long cohortId = cohort();
        long reviewId = application(cohortId, 901L, CohortApplicationStatus.NEEDS_REVIEW, ApplicantGender.FEMALE,
                "MALE", 0, null, null);
        long queuedId = application(cohortId, 902L, CohortApplicationStatus.QUEUED, ApplicantGender.MALE, "MALE", 0,
                NOW, null);
        em.flush();

        send(patch("/api/v1/admin/cohort-applications/" + reviewId + "/memo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\":\"  문의 완료  \"}"), "OPERATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.admin_memo").value("문의 완료"));
        send(post("/api/v1/admin/cohort-applications/" + reviewId + "/approve"), "OPERATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.gender").value("MALE"))
                .andExpect(jsonPath("$.data.queued_at").exists())
                .andExpect(jsonPath("$.data.admin_memo").value("문의 완료"));
        send(post("/api/v1/admin/cohort-applications/" + queuedId + "/approve"), "OPERATOR")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COHORT_APPLICATION_NOT_APPROVABLE"));
        send(patch("/api/v1/admin/cohort-applications/" + reviewId + "/memo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\":\"\"}"), "OPERATOR")
                .andExpect(jsonPath("$.data.admin_memo").doesNotExist());
        send(patch("/api/v1/admin/cohort-applications/" + reviewId + "/memo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\":\"" + "가".repeat(501) + "\"}"), "OPERATOR")
                .andExpect(status().isBadRequest());
        send(patch("/api/v1/admin/cohort-applications/999999/memo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\":\"x\"}"), "OPERATOR")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("번호가 들어 있어 CS 는 신청자 표를 보거나 고칠 수 없다")
    void cs_isForbidden() throws Exception {
        long cohortId = cohort();
        send(get("/api/v1/admin/cohorts/" + cohortId + "/applications"), "CS").andExpect(status().isForbidden());
        send(post("/api/v1/admin/cohort-applications/1/approve"), "CS").andExpect(status().isForbidden());
        send(patch("/api/v1/admin/cohort-applications/1/memo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\":\"x\"}"), "CS").andExpect(status().isForbidden());
    }
}
