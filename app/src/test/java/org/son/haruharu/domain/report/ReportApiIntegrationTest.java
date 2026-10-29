package org.son.haruharu.domain.report;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.son.haruharu.domain.report.storage.ReportJpaRepository;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired ReportJpaRepository reportJpaRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    Long reporterId;
    Long reportedId;

    // "이 두 유저 사이에 신고가 없다"는 전제를 스스로 보장한다(#166 격리 원칙 — native DELETE로 물리 정리)
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM reports");
        reporterId = seedUser();
        reportedId = seedUser();
    }

    private Long seedUser() {
        return userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("유저 신고 접수 시 report_id(snake_case)를 반환하고 RECEIVED로 저장된다")
    void givenValidRequest_whenReport_thenCreated() throws Exception {
        mockMvc.perform(post("/api/v1/reports").with(authentication(auth(reporterId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reported_user_id": %d, "reason": "ABUSIVE_LANGUAGE", "detail": "욕설"}
                                """.formatted(reportedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.report_id").isNumber());

        assertThat(reportJpaRepository.findAll()).singleElement()
                .satisfies(e -> assertThat(e.toDomain().status().name()).isEqualTo("RECEIVED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CHILD_SAFETY", "INAPPROPRIATE_MEDIA", "ABUSIVE_LANGUAGE", "FAKE_PROFILE", "SPAM_AD", "OTHER"})
    @DisplayName("아동 안전을 포함한 모든 신고 사유는 상세 내용과 함께 저장되고 접수번호를 반환한다")
    void givenSupportedReason_whenReport_thenReasonAndDetailAreStored(String reason) throws Exception {
        var response = mockMvc.perform(post("/api/v1/reports").with(authentication(auth(reporterId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reported_user_id": %d, "reason": "%s", "detail": "안전 우려 상세 내용"}
                                """.formatted(reportedId, reason)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        var report = reportJpaRepository.findAll().getFirst().toDomain();
        assertThat(report.reason().name()).isEqualTo(reason);
        assertThat(report.detail()).isEqualTo("안전 우려 상세 내용");
        assertThat(report.reporterUserId()).isEqualTo(reporterId);
        assertThat(report.reportedUserId()).isEqualTo(reportedId);
        assertThat(report.status().name()).isEqualTo("RECEIVED");
        response.andExpect(jsonPath("$.data.report_id").value(report.id()));
    }

    @Test
    @DisplayName("같은 대상 재신고 시 409 REPORT_ALREADY_EXISTS를 반환한다")
    void givenDuplicate_whenReport_thenConflict() throws Exception {
        String body = """
                {"reported_user_id": %d, "reason": "SPAM_AD"}
                """.formatted(reportedId);
        mockMvc.perform(post("/api/v1/reports").with(authentication(auth(reporterId)))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/reports").with(authentication(auth(reporterId)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("자기 자신 신고 시 400 CANNOT_REPORT_SELF를 반환한다")
    void givenSelf_whenReport_thenBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/reports").with(authentication(auth(reporterId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reported_user_id": %d, "reason": "OTHER"}
                                """.formatted(reporterId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CANNOT_REPORT_SELF"));
    }

    @Test
    @DisplayName("존재하지 않는 유저 신고 시 404를 반환한다")
    void givenUnknownTarget_whenReport_thenNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/reports").with(authentication(auth(reporterId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reported_user_id\": 999999, \"reason\": \"OTHER\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("인증 없이 신고 시 401을 반환한다")
    void givenNoAuth_whenReport_thenUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reported_user_id\": 2, \"reason\": \"OTHER\"}"))
                .andExpect(status().isUnauthorized());
    }
}
