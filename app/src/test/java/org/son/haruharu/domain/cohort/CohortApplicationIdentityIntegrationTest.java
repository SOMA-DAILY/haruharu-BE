package org.son.haruharu.domain.cohort;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.cohort.business.CohortApplicationScreeningService;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.son.haruharu.domain.cohort.implement.CohortWriter;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;
import org.son.haruharu.domain.cohort.implement.RecruitTrackWriter;
import org.son.haruharu.domain.profile.implement.Gender;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileWriter;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.business.command.TicketCreditCommand;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.infrastructure.pass.PassVerificationPort;
import org.son.haruharu.infrastructure.pass.PassVerificationResult;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PASS 완료 → 기수 신청 상태 전이(#512)를 HTTP 로 확인한다. 결제(신청) 뒤 PASS 를 하면 커밋 후 리스너가
 * PASS 대기 신청을 편성 대기·확인 필요로 넘기고, 리스너가 실패해도 내 신청 조회가 다시 판정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CohortApplicationIdentityIntegrationTest {

    private static final String APPLY_BODY = """
            {"school": "한국대", "department": "컴퓨터공학과", "phone": "010-1234-5678", "answers": {}}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired RecruitTrackWriter trackWriter;
    @Autowired CohortWriter cohortWriter;
    @Autowired TicketService ticketService;
    @Autowired UserProfileWriter userProfileWriter;
    @MockitoBean PassVerificationPort passVerificationPort;
    @MockitoSpyBean CohortApplicationScreeningService screeningService;

    private Long userId;
    private String token;
    private Cohort cohort;

    @BeforeEach
    void setUp() {
        deleteAll();
        userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        token = jwtProvider.generateAccessToken(userId);
        ticketService.createWallet(userId);
        ticketService.credit(TicketCreditCommand.adminGrant(userId, 1, 1L, "테스트"));
        RecruitTrack track = trackWriter.save(RecruitTrack.create("seoul-student", "서울 대학생", "서울",
                RecruitAudience.STUDENT, 20, 29, List.of(), 1));
        LocalDateTime now = LocalDateTime.now();
        cohort = cohortWriter.save(new Cohort(null, track.id(), 6, CohortStatus.OPEN, now.minusDays(1),
                now.plusDays(3), now.plusDays(4), "10월 마지막 주", 6, 6, null, "admin:1", null, null));
    }

    /** 같은 H2 를 쓰는 다른 테스트에 행을 남기지 않는다(soft-delete 유니크가 남지 않게 SQL 로 지운다). */
    @AfterEach
    void deleteAll() {
        for (String table : List.of("cohort_applications", "cohorts", "recruit_tracks", "ticket_ledger",
                "ticket_wallet", "user_profiles", "user_identities", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    private void profile(Gender gender) {
        userProfileWriter.save(new UserProfile(null, userId, null, null, null, null, "닉", null, null, gender));
    }

    private void applyPending() throws Exception {
        mockMvc.perform(post("/api/v1/recruit/cohorts/{id}/applications", cohort.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(APPLY_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IDENTITY_PENDING"));
    }

    private ResultActions verifyPass(String gender, LocalDate birthday) throws Exception {
        given(passVerificationPort.verify("iv-token")).willReturn(new PassVerificationResult("ci-" + userId,
                "di-" + userId, "01099998888", birthday, gender, "홍길동", "SKT", false));
        return mockMvc.perform(post("/api/v1/users/me/identity")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pass_token\": \"iv-token\"}"));
    }

    private ResultActions getMine() throws Exception {
        return mockMvc.perform(get("/api/v1/recruit/applications/me").header("Authorization", "Bearer " + token));
    }

    private Map<String, Object> row() {
        return jdbcTemplate.queryForMap("SELECT status, review_reason, declared_gender, gender, birthday, queued_at"
                + " FROM cohort_applications WHERE user_id = ?", userId);
    }

    private static LocalDate age(int yearAge) {
        return LocalDate.of(LocalDate.now().getYear() - yearAge, 1, 1);
    }

    private void assertTicketKept() {
        assertThat(ticketService.getWallet(userId).balance()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ticket_ledger WHERE user_id = ?"
                + " AND reason = 'USE_REFUND'", Long.class, userId)).isZero();
    }

    @Test
    @DisplayName("PASS 성별·나이가 맞으면 PASS 직후 편성 대기가 되고 PASS 성별·생년을 스냅샷하며, 참가권은 쓴 채로 둔다")
    void givenMatchingPass_whenVerifyIdentity_thenQueued() throws Exception {
        profile(Gender.MALE);
        applyPending();

        verifyPass("MALE", age(23)).andExpect(status().isOk());

        Map<String, Object> row = row();
        assertThat(row.get("status")).isEqualTo("QUEUED");
        assertThat(row.get("review_reason")).isNull();
        assertThat(row.get("gender")).isEqualTo("MALE");
        assertThat(row.get("birthday").toString()).isEqualTo(age(23).toString());
        assertThat(row.get("queued_at")).isNotNull();
        assertTicketKept();
        getMine().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.application.is_cancellable").value(true));
    }

    @Test
    @DisplayName("PASS 성별이 프로필 성별과 다르면 확인 필요(GENDER_MISMATCH)이고 참가권은 그대로다")
    void givenGenderMismatch_whenVerifyIdentity_thenNeedsReview() throws Exception {
        profile(Gender.FEMALE);
        applyPending();

        verifyPass("MALE", age(23)).andExpect(status().isOk());

        assertThat(row().get("status")).isEqualTo("NEEDS_REVIEW");
        assertThat(row().get("review_reason")).isEqualTo("GENDER_MISMATCH");
        assertThat(row().get("queued_at")).isNull();
        assertTicketKept();
        getMine().andExpect(jsonPath("$.data.application.status").value("NEEDS_REVIEW"));
    }

    @Test
    @DisplayName("PASS 성별이 OTHER 면 확인 필요(UNKNOWN_GENDER)")
    void givenOtherGender_whenVerifyIdentity_thenUnknownGender() throws Exception {
        profile(Gender.MALE);
        applyPending();

        verifyPass("OTHER", age(23)).andExpect(status().isOk());

        assertThat(row().get("status")).isEqualTo("NEEDS_REVIEW");
        assertThat(row().get("review_reason")).isEqualTo("UNKNOWN_GENDER");
        assertThat(row().get("gender")).isEqualTo("OTHER");
        assertTicketKept();
    }

    @Test
    @DisplayName("연 나이가 트랙 범위(20~29) 밖이면 확인 필요(AGE_OUT_OF_RANGE)")
    void givenAgeOutOfRange_whenVerifyIdentity_thenAgeOut() throws Exception {
        profile(Gender.MALE);
        applyPending();

        verifyPass("MALE", age(35)).andExpect(status().isOk());

        assertThat(row().get("status")).isEqualTo("NEEDS_REVIEW");
        assertThat(row().get("review_reason")).isEqualTo("AGE_OUT_OF_RANGE");
        assertTicketKept();
    }

    @Test
    @DisplayName("프로필 성별이 비어 있으면 PASS 성별을 신청 성별로 쓰고 편성 대기가 된다")
    void givenNoProfileGender_whenVerifyIdentity_thenDeclaredFromPass() throws Exception {
        profile(null);
        applyPending();

        verifyPass("FEMALE", age(23)).andExpect(status().isOk());

        assertThat(row().get("status")).isEqualTo("QUEUED");
        assertThat(row().get("declared_gender")).isEqualTo("FEMALE");
    }

    @Test
    @DisplayName("신청 판정이 실패해도 PASS 는 200 으로 저장되고, 내 신청 조회가 다시 판정해 편성 대기로 복구한다")
    void givenListenerFails_whenGetMine_thenRecovered() throws Exception {
        profile(Gender.MALE);
        applyPending();
        willThrow(new IllegalStateException("screening down")).given(screeningService)
                .screenAfterIdentityVerified(anyLong());

        verifyPass("MALE", age(23)).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_identities WHERE user_id = ?",
                Long.class, userId)).isEqualTo(1);
        assertThat(row().get("status")).isEqualTo("IDENTITY_PENDING");

        getMine().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.status").value("QUEUED"));
        assertThat(row().get("status")).isEqualTo("QUEUED");
        assertTicketKept();
    }

    @Test
    @DisplayName("PASS 가 실패하면(미성년) 신청은 PASS 대기로 남는다")
    void givenUnderagePass_whenVerifyIdentity_thenStillPending() throws Exception {
        profile(Gender.MALE);
        applyPending();

        verifyPass("MALE", LocalDate.now().minusYears(18)).andExpect(status().isForbidden());

        verify(screeningService, never()).screenAfterIdentityVerified(anyLong());
        assertThat(row().get("status")).isEqualTo("IDENTITY_PENDING");
        getMine().andExpect(jsonPath("$.data.application.status").value("IDENTITY_PENDING"));
    }
}
