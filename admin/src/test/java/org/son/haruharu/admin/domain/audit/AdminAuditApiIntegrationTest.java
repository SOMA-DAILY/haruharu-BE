package org.son.haruharu.admin.domain.audit;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.admin.domain.account.implement.AdminAccount;
import org.son.haruharu.admin.domain.account.implement.AdminRole;
import org.son.haruharu.admin.domain.account.storage.AdminAccountEntity;
import org.son.haruharu.admin.domain.account.storage.AdminAccountJpaRepository;
import org.son.haruharu.admin.domain.audit.implement.AdminAccessLog;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditAction;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditTargetType;
import org.son.haruharu.admin.domain.audit.storage.AdminAccessLogEntity;
import org.son.haruharu.admin.domain.audit.storage.AdminAccessLogJpaRepository;
import org.son.haruharu.admin.domain.audit.storage.AdminAuditReviewJpaRepository;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #554 접속기록. 인터셉터가 REQUIRES_NEW 로 커밋하므로 테스트 트랜잭션 롤백에 기대지 않고 직접 정리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuditApiIntegrationTest {

    private static final String PASSWORD = "P@ssw0rd!";

    @Autowired MockMvc mockMvc;
    @Autowired AdminAccountJpaRepository adminAccountJpaRepository;
    @Autowired AdminAccessLogJpaRepository accessLogJpaRepository;
    @Autowired AdminAuditReviewJpaRepository reviewJpaRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired EntityManager em;

    private Long superId;
    private Long operatorId;
    private String superEmail;
    private Long userId;

    @BeforeEach
    void seed() {
        accessLogJpaRepository.deleteAllInBatch();
        reviewJpaRepository.deleteAllInBatch();
        String suffix = Long.toHexString(System.nanoTime());
        superEmail = "audit-super-" + suffix + "@haruharu.app";
        superId = adminAccountJpaRepository.save(AdminAccountEntity.from(
                AdminAccount.create(superEmail, passwordEncoder.encode(PASSWORD), "감사슈퍼", AdminRole.SUPER))).toDomain().id();
        operatorId = adminAccountJpaRepository.save(AdminAccountEntity.from(AdminAccount.create(
                "audit-op-" + suffix + "@haruharu.app", passwordEncoder.encode(PASSWORD), "감사운영", AdminRole.OPERATOR)))
                .toDomain().id();
        userId = transactionTemplate.execute(status -> {
            UserEntity user = UserEntity.from(new User(null, UserStatus.ACTIVE, null));
            em.persist(user);
            return user.toDomain().id();
        });
    }

    @AfterEach
    void cleanUp() {
        accessLogJpaRepository.deleteAllInBatch();
        reviewJpaRepository.deleteAllInBatch();
    }

    private static UsernamePasswordAuthenticationToken auth(Long adminId, AdminRole role) {
        return new UsernamePasswordAuthenticationToken(adminId, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private List<AdminAccessLog> logs(AdminAuditAction action) {
        return accessLogJpaRepository.findAll().stream().map(AdminAccessLogEntity::toDomain)
                .filter(log -> log.action() == action).toList();
    }

    @Test
    @DisplayName("로그인 성공은 LOGIN 에 관리자 id, 실패는 LOGIN_FAIL 에 입력 이메일만 남는다")
    void givenLoginAttempts_whenHandled_thenLoginRowsRecorded() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "198.51.100.1, 203.0.113.9")
                        .content("{\"email\":\"" + superEmail + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + superEmail + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        List<AdminAccessLog> success = logs(AdminAuditAction.LOGIN);
        assertThat(success).hasSize(1);
        assertThat(success.get(0).adminId()).isEqualTo(superId);
        assertThat(success.get(0).ip()).isEqualTo("203.0.113.9");
        assertThat(success.get(0).statusCode()).isEqualTo(200);
        assertThat(success.get(0).detail()).isNull();

        List<AdminAccessLog> failed = logs(AdminAuditAction.LOGIN_FAIL);
        assertThat(failed).hasSize(1);
        assertThat(failed.get(0).adminId()).isNull();
        assertThat(failed.get(0).statusCode()).isEqualTo(401);
        assertThat(failed.get(0).detail()).isEqualTo("email=" + superEmail);
    }

    @Test
    @DisplayName("유저 상세 조회는 요청마다 VIEW_USER 1행(대상 USER id)이 남고 본문은 저장되지 않는다")
    void givenUserDetailView_whenHandled_thenViewUserRowWithoutBody() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/{userId}", userId).with(authentication(auth(superId, AdminRole.SUPER))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/me").with(authentication(auth(superId, AdminRole.SUPER))));

        List<AdminAccessLog> rows = logs(AdminAuditAction.VIEW_USER);
        assertThat(rows).hasSize(1);
        AdminAccessLog row = rows.get(0);
        assertThat(row.adminId()).isEqualTo(superId);
        assertThat(row.targetType()).isEqualTo(AdminAuditTargetType.USER);
        assertThat(row.targetId()).isEqualTo(userId);
        assertThat(row.path()).isEqualTo("/api/v1/admin/users/" + userId);
        assertThat(row.method()).isEqualTo("GET");
        assertThat(row.reason()).isNull();
        assertThat(row.detail()).isNull();
        assertThat(accessLogJpaRepository.findAll()).extracting(e -> e.toDomain().path())
                .doesNotContain("/api/v1/admin/me");
    }

    @Test
    @DisplayName("반출 사유가 5자 미만이면 400 이고 행이 남지 않으며, 정상 사유는 EXPORT 행과 log_id 를 돌려준다")
    void givenExportRegistration_whenReasonMissingOrValid_thenRejectedOrRecorded() throws Exception {
        var operator = authentication(auth(operatorId, AdminRole.OPERATOR));

        mockMvc.perform(post("/api/v1/admin/audit/exports").with(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"ROOM\",\"target_id\":107,\"scope\":\"videos\",\"reason\":\"짧음\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        assertThat(logs(AdminAuditAction.EXPORT)).isEmpty();

        mockMvc.perform(post("/api/v1/admin/audit/exports").with(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"ROOM\",\"target_id\":107,\"scope\":\"videos+chats\","
                                + "\"reason\":\"신고 2026-11-04 접수건 조사용 영상 확인\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.log_id").isNumber());

        List<AdminAccessLog> exports = logs(AdminAuditAction.EXPORT);
        assertThat(exports).hasSize(1);
        assertThat(exports.get(0).adminId()).isEqualTo(operatorId);
        assertThat(exports.get(0).targetType()).isEqualTo(AdminAuditTargetType.ROOM);
        assertThat(exports.get(0).targetId()).isEqualTo(107L);
        assertThat(exports.get(0).reason()).isEqualTo("신고 2026-11-04 접수건 조사용 영상 확인");
        assertThat(exports.get(0).detail()).isEqualTo("videos+chats");
        // 반출 등록 요청 자체는 인터셉터가 CREATE 로 중복 기록하지 않는다.
        assertThat(logs(AdminAuditAction.CREATE)).isEmpty();
    }

    @Test
    @DisplayName("접속기록 목록·요약·점검은 SUPER 만 볼 수 있고 OPERATOR 는 403")
    void givenOperator_whenReadAudit_thenForbidden() throws Exception {
        var operator = authentication(auth(operatorId, AdminRole.OPERATOR));
        mockMvc.perform(get("/api/v1/admin/audit/logs").with(operator)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/audit/summary").with(operator)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/audit/reviews").with(operator)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/audit/reviews").with(operator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"period\":\"2026-11\"}")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("목록은 관리자·행위·대상·기간 필터와 페이지가 적용되고 관리자 이름이 붙는다")
    void givenRows_whenSearchLogs_thenFilteredAndPaged() throws Exception {
        var superAuth = authentication(auth(superId, AdminRole.SUPER));
        var operatorAuth = authentication(auth(operatorId, AdminRole.OPERATOR));
        mockMvc.perform(get("/api/v1/admin/users/{userId}", userId).with(superAuth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/users").with(operatorAuth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/users").with(operatorAuth)).andExpect(status().isOk());
        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/admin/audit/logs").with(superAuth)
                        .param("adminId", operatorId.toString()).param("from", today).param("to", today)
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.page.total_elements").value(2))
                .andExpect(jsonPath("$.data.content[0].action").value("VIEW_USERS"))
                .andExpect(jsonPath("$.data.content[0].admin.id").value(operatorId))
                .andExpect(jsonPath("$.data.content[0].admin.name").value("감사운영"));

        mockMvc.perform(get("/api/v1/admin/audit/logs").with(superAuth)
                        .param("targetType", "USER").param("targetId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value("VIEW_USER"))
                .andExpect(jsonPath("$.data.content[0].target_id").value(userId));

        mockMvc.perform(get("/api/v1/admin/audit/logs").with(superAuth).param("from", today).param("to", "2020-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUDIT_INVALID_RANGE"));
    }

    @Test
    @DisplayName("요약은 관리자별 조회·반출 수를 내고 하루 200건 이상 조회한 관리자를 대량 조회로 올린다")
    void givenHeavyViewer_whenSummary_thenCountsAndHeavyViewers() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        List<AdminAccessLogEntity> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            rows.add(AdminAccessLogEntity.from(AdminAccessLog.of(operatorId, now.minusMinutes(i), "10.0.0.1", "GET",
                    "/api/v1/admin/users/" + i, AdminAuditAction.VIEW_USER, AdminAuditTargetType.USER, (long) i, 200,
                    null, null, null)));
        }
        rows.add(AdminAccessLogEntity.from(AdminAccessLog.of(superId, now, "10.0.0.2", "POST", "/api/v1/admin/audit/exports",
                AdminAuditAction.EXPORT, AdminAuditTargetType.ROOM, 1L, 200, "조사용 반출 사유", null, null)));
        accessLogJpaRepository.saveAll(rows);

        mockMvc.perform(get("/api/v1/admin/audit/summary").with(authentication(auth(superId, AdminRole.SUPER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.heavy_view_threshold").value(200))
                .andExpect(jsonPath("$.data.per_admin[?(@.admin.id == " + operatorId + ")].views").value(200))
                .andExpect(jsonPath("$.data.per_admin[?(@.admin.id == " + superId + ")].exports").value(1))
                .andExpect(jsonPath("$.data.heavy_viewers.length()").value(1))
                .andExpect(jsonPath("$.data.heavy_viewers[0].admin.id").value(operatorId))
                .andExpect(jsonPath("$.data.heavy_viewers[0].count").value(200));
    }

    @Test
    @DisplayName("정기 점검은 YYYY-MM 만 받고, 기록 뒤 최근순으로 조회된다")
    void givenReviewRequests_whenCreateAndList_thenValidatedAndOrdered() throws Exception {
        var superAuth = authentication(auth(superId, AdminRole.SUPER));
        mockMvc.perform(post("/api/v1/admin/audit/reviews").with(superAuth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026/11\",\"note\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUDIT_INVALID_PERIOD"));

        mockMvc.perform(post("/api/v1/admin/audit/reviews").with(superAuth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-10\",\"note\":\"이상 없음\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("2026-10"))
                .andExpect(jsonPath("$.data.admin.name").value("감사슈퍼"));
        mockMvc.perform(post("/api/v1/admin/audit/reviews").with(superAuth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-11\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/audit/reviews").with(superAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].period").value("2026-11"))
                .andExpect(jsonPath("$.data[1].note").value("이상 없음"));
    }
}
