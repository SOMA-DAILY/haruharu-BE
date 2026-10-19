package org.son.haruharu.domain.cohort;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.son.haruharu.domain.cohort.implement.CohortWriter;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;
import org.son.haruharu.domain.cohort.implement.RecruitTrackWriter;
import org.son.haruharu.domain.cohort.implement.TrackQuestion;
import org.son.haruharu.domain.cohort.implement.TrackQuestionType;
import org.son.haruharu.domain.identity.implement.UserIdentity;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.son.haruharu.domain.profile.implement.Gender;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.implement.UserProfileWriter;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.business.command.TicketCreditCommand;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 앱 기수 신청(#511): 신청·참가권 차감·내 신청·설문·배정 전 취소를 HTTP 로 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CohortApplicationApiIntegrationTest {

    private static final String APPLY_BODY = """
            {"school": " 한국대 ", "department": "컴퓨터공학과", "phone": "010-1234-5678", "answers": {"grade": "3학년"}}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired RecruitTrackWriter trackWriter;
    @Autowired CohortWriter cohortWriter;
    @Autowired TicketService ticketService;
    @Autowired UserProfileReader userProfileReader;
    @Autowired UserProfileWriter userProfileWriter;
    @Autowired UserIdentityWriter userIdentityWriter;

    private Long userId;
    private String token;
    private RecruitTrack track;

    @BeforeEach
    void setUp() {
        deleteAll();
        userId = user();
        token = jwtProvider.generateAccessToken(userId);
        track = trackWriter.save(RecruitTrack.create("seoul-student", "서울 대학생", "서울", RecruitAudience.STUDENT,
                20, 29, List.of(new TrackQuestion("grade", "학년", TrackQuestionType.CHOICE, true,
                        List.of("1학년", "2학년", "3학년"))), 1));
    }

    /**
     * 같은 H2 를 쓰는 다른 테스트에 행을 남기지 않는다. 엔티티 삭제는 soft-delete 라 유니크(트랙 키 등)가 남으므로
     * SQL 로 실제로 지운다.
     */
    @AfterEach
    void deleteAll() {
        for (String table : List.of("cohort_applications", "cohorts", "recruit_tracks", "ticket_ledger",
                "ticket_wallet", "user_profiles", "user_identities", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    private Long user() {
        Long id = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        ticketService.createWallet(id);
        return id;
    }

    private void grantTickets(Long user, long amount) {
        ticketService.credit(TicketCreditCommand.adminGrant(user, amount, 1L, "테스트"));
    }

    /** 공개된 기수. 마감이 지난 공개 기수도 만들 수 있게 publish() 를 거치지 않고 저장한다. */
    private Cohort openCohort(LocalDateTime opensAt, LocalDateTime closesAt) {
        return cohortWriter.save(new Cohort(null, track.id(), 6, CohortStatus.OPEN, opensAt, closesAt,
                closesAt.plusDays(1), "10월 마지막 주", 6, 6, null, "admin:1", null, null));
    }

    private Cohort openCohort() {
        LocalDateTime now = LocalDateTime.now();
        return openCohort(now.minusDays(1), now.plusDays(3));
    }

    private ResultActions apply(String bearer, Long cohortId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/recruit/cohorts/{id}/applications", cohortId)
                .header("Authorization", "Bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long balance(Long user) {
        return ticketService.getWallet(user).balance();
    }

    private Long applicationId() {
        return jdbcTemplate.queryForObject("SELECT max(id) FROM cohort_applications WHERE user_id = ?", Long.class,
                userId);
    }

    @Test
    @DisplayName("신청하면 참가권 1장이 바로 빠지고, PASS 전이라 PASS 대기로 남으며, 학교·학과·번호가 프로필에 저장된다")
    void givenTicket_whenApply_thenSpendsAndSavesContact() throws Exception {
        Cohort cohort = openCohort();
        grantTickets(userId, 1);

        apply(token, cohort.id(), APPLY_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application_id").value(notNullValue()))
                .andExpect(jsonPath("$.data.cohort_name").value("서울 대학생 6기"))
                .andExpect(jsonPath("$.data.status").value("IDENTITY_PENDING"))
                .andExpect(jsonPath("$.data.school").value("한국대"))
                .andExpect(jsonPath("$.data.answers.grade").value("3학년"))
                .andExpect(jsonPath("$.data.is_cancellable").value(true))
                .andExpect(jsonPath("$.data.refunded").value(false));

        Long applicationId = applicationId();
        assertThat(balance(userId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ticket_ledger WHERE user_id = ? AND reason = 'USE'"
                + " AND ref_type = 'COHORT_APPLICATION' AND ref_id = ? AND delta = -1", Long.class,
                userId, applicationId.toString())).isEqualTo(1);
        UserProfile profile = userProfileReader.getByUserId(userId);
        assertThat(profile.affiliation()).isEqualTo("한국대");
        assertThat(profile.department()).isEqualTo("컴퓨터공학과");
        assertThat(profile.phone()).isEqualTo("01012345678");

        mockMvc.perform(get("/api/v1/recruit/applications/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.application_id").value(applicationId))
                .andExpect(jsonPath("$.data.application.survey_submitted_at").value(nullValue()))
                .andExpect(jsonPath("$.data.defaults.school").value("한국대"))
                .andExpect(jsonPath("$.data.defaults.department").value("컴퓨터공학과"))
                .andExpect(jsonPath("$.data.defaults.phone").value("01012345678"));
    }

    @Test
    @DisplayName("PASS 를 이미 했고 성별·나이가 맞으면 신청 즉시 편성 대기가 된다")
    void givenVerifiedUser_whenApply_thenQueued() throws Exception {
        Cohort cohort = openCohort();
        grantTickets(userId, 1);
        userProfileWriter.save(new UserProfile(null, userId, null, null, null, null, "닉", null, null, Gender.FEMALE));
        LocalDate birthday = LocalDate.now().minusYears(23);
        userIdentityWriter.save(new UserIdentity(null, userId, "ci-" + userId, "di-" + userId, "01099998888",
                birthday, "FEMALE", "홍길동", "SKT", false, LocalDateTime.now()));

        apply(token, cohort.id(), APPLY_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
        assertThat(jdbcTemplate.queryForObject("SELECT gender FROM cohort_applications WHERE user_id = ?",
                String.class, userId)).isEqualTo("FEMALE");
    }

    @Test
    @DisplayName("참가권이 없으면 400 TICKET_INSUFFICIENT 이고 신청도 프로필 변경도 남지 않는다")
    void givenNoTicket_whenApply_thenRejectedWithoutTrace() throws Exception {
        Cohort cohort = openCohort();

        apply(token, cohort.id(), APPLY_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("TICKET_INSUFFICIENT"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM cohort_applications", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ticket_ledger WHERE reason = 'USE'", Long.class))
                .isZero();
        assertThat(userProfileReader.findByUserId(userId)).isEmpty();
        mockMvc.perform(get("/api/v1/recruit/applications/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.application").value(nullValue()))
                .andExpect(jsonPath("$.data.defaults.school").value(nullValue()))
                .andExpect(jsonPath("$.data.defaults.phone").value(nullValue()));
    }

    @Test
    @DisplayName("칸 오류는 400 VALIDATION_ERROR 로 칸 이름과 함께 알려주고 참가권을 쓰지 않는다")
    void givenInvalidForm_whenApply_thenFieldErrors() throws Exception {
        Cohort cohort = openCohort();
        grantTickets(userId, 1);

        apply(token, cohort.id(), """
                {"school": "", "department": "컴퓨터공학과", "phone": "02-123-4567", "answers": {"grade": "5학년"}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields[0].field").value("school"))
                .andExpect(jsonPath("$.error.fields[1].field").value("phone"))
                .andExpect(jsonPath("$.error.fields[2].field").value("answers.grade"));
        assertThat(balance(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("신청 기간이 지난 기수는 409 COHORT_NOT_ACCEPTING")
    void givenClosedWindow_whenApply_thenNotAccepting() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Cohort cohort = openCohort(now.minusDays(5), now.minusMinutes(1));
        grantTickets(userId, 1);

        apply(token, cohort.id(), APPLY_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COHORT_NOT_ACCEPTING"));
        assertThat(balance(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("진행 중 신청이 있으면 두 번째 신청은 409 이고 참가권은 한 장만 빠진다")
    void givenInProgress_whenApplyAgain_thenConflict() throws Exception {
        Cohort cohort = openCohort();
        grantTickets(userId, 2);
        apply(token, cohort.id(), APPLY_BODY).andExpect(status().isOk());

        apply(token, cohort.id(), APPLY_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COHORT_APPLICATION_IN_PROGRESS"));
        assertThat(balance(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("설문은 건너뛴 뒤에도 내고 고칠 수 있고, 신청 상태는 그대로다")
    void givenApplication_whenSubmitSurvey_thenSaved() throws Exception {
        Cohort cohort = openCohort();
        grantTickets(userId, 1);
        apply(token, cohort.id(), APPLY_BODY).andExpect(status().isOk());
        Long applicationId = applicationId();

        mockMvc.perform(put("/api/v1/recruit/applications/{id}/survey", applicationId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"referral_source": "인스타그램", "friend_preference": "SAME_ROOM", "motivation": "설레요"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referral_source").value("인스타그램"))
                .andExpect(jsonPath("$.data.friend_preference").value("SAME_ROOM"))
                .andExpect(jsonPath("$.data.survey_submitted_at").value(notNullValue()))
                .andExpect(jsonPath("$.data.status").value("IDENTITY_PENDING"));

        mockMvc.perform(put("/api/v1/recruit/applications/{id}/survey", applicationId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referral_source\": \"친구\", \"friend_preference\": \"ALONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friend_preference").value("ALONE"))
                .andExpect(jsonPath("$.data.motivation").value(nullValue()));
    }

    @Test
    @DisplayName("배정 전 취소는 참가권을 돌려주고, 두 번째 취소는 409, 그 뒤 다시 신청할 수 있다")
    void givenApplication_whenCancel_thenRefundedOnceAndCanReapply() throws Exception {
        Cohort cohort = openCohort();
        grantTickets(userId, 1);
        apply(token, cohort.id(), APPLY_BODY).andExpect(status().isOk());
        Long applicationId = applicationId();

        mockMvc.perform(post("/api/v1/recruit/applications/{id}/cancel", applicationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.close_reason").value("USER_CANCELLED"))
                .andExpect(jsonPath("$.data.refunded").value(true))
                .andExpect(jsonPath("$.data.is_cancellable").value(false));
        assertThat(balance(userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ticket_ledger WHERE reason = 'USE_REFUND'"
                + " AND ref_type = 'COHORT_APPLICATION' AND ref_id = ?", Long.class, applicationId.toString()))
                .isEqualTo(1);

        mockMvc.perform(post("/api/v1/recruit/applications/{id}/cancel", applicationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COHORT_APPLICATION_NOT_CANCELLABLE"));
        mockMvc.perform(put("/api/v1/recruit/applications/{id}/survey", applicationId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referral_source\": \"친구\", \"friend_preference\": \"ALONE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COHORT_APPLICATION_CLOSED"));
        assertThat(balance(userId)).isEqualTo(1);

        apply(token, cohort.id(), APPLY_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IDENTITY_PENDING"));
        assertThat(balance(userId)).isZero();
    }

    @Test
    @DisplayName("남의 신청은 취소·설문 모두 404 이고 아무것도 바뀌지 않는다")
    void givenOthersApplication_whenCancel_thenNotFound() throws Exception {
        Cohort cohort = openCohort();
        grantTickets(userId, 1);
        apply(token, cohort.id(), APPLY_BODY).andExpect(status().isOk());
        Long applicationId = applicationId();
        String otherToken = jwtProvider.generateAccessToken(user());

        mockMvc.perform(post("/api/v1/recruit/applications/{id}/cancel", applicationId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COHORT_APPLICATION_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/recruit/applications/{id}/survey", applicationId)
                        .header("Authorization", "Bearer " + otherToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referral_source\": \"친구\", \"friend_preference\": \"ALONE\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM cohort_applications WHERE id = ?", String.class,
                applicationId)).isEqualTo("IDENTITY_PENDING");
    }

    @Test
    @DisplayName("로그인하지 않으면 신청은 401")
    void givenNoToken_whenApply_thenUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/recruit/cohorts/{id}/applications", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content(APPLY_BODY))
                .andExpect(status().isUnauthorized());
    }
}
