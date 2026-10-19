package org.son.haruharu.batch.api;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.batch.notificationcleanup.NotificationCleanupJobLauncher;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내부 배치 트리거 API end-to-end(#389). 실제 JobOperator + JDBC JobRepository(H2) 위에서
 * 시작·멱등 재호출·FAILED 재시작·상태 조회·인증·미등록 잡을 HTTP 로 검증한다.
 * 버킷은 테스트마다 유일하게 만들어 컨텍스트 공유로 누적된 BATCH_* 행과 간섭하지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalBatchApiIntegrationTest {

    private static final String JOB = "notificationCleanupJob";

    // application-test.yml 의 값(환경변수 INTERNAL_BATCH_TOKEN 이 있으면 그것). 상수로 박으면 환경에 따라 어긋난다.
    @Value("${haruharu.batch.internal.token}") String token;

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ApplicationContext applicationContext;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired JwtProvider jwtProvider;

    private static String uniqueBucket() {
        return "2026-09-04-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String body(String bucket) {
        return "{\"bucket\":\"" + bucket + "\"}";
    }

    private MvcResult start(String job, String bucket) throws Exception {
        return mockMvc.perform(post("/internal/batch/jobs/{job}/executions", job)
                        .header("X-Internal-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(bucket)))
                .andReturn();
    }

    private long executionIdOf(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("execution_id").asLong();
    }

    private int instanceCount(String bucket) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM BATCH_JOB_EXECUTION_PARAMS
                WHERE PARAMETER_NAME = 'bucket' AND PARAMETER_VALUE = ?""", Integer.class, bucket);
    }

    @Test
    @DisplayName("폐기된 미션 배정·집계 잡은 내부 API로도 실행할 수 없다")
    void givenRetiredMissionJobs_whenStart_then404WithoutExecution() throws Exception {
        for (String job : java.util.List.of("missionAssignmentJob", "missionOutcomeJob")) {
            String bucket = uniqueBucket();
            mockMvc.perform(post("/internal/batch/jobs/{job}/executions", job)
                            .header("X-Internal-Token", token)
                            .contentType(MediaType.APPLICATION_JSON).content(body(bucket)))
                    .andExpect(status().isNotFound());
            assertThat(instanceCount(bucket)).isZero();
        }
    }

    @Test
    @DisplayName("시나리오 1: 새 버킷으로 시작하면 202·STARTED 와 executionId 를 받고 BATCH_JOB_INSTANCE 행이 생긴다")
    void givenNewBucket_whenStart_then202AndInstancePersisted() throws Exception {
        String bucket = uniqueBucket();

        MvcResult result = mockMvc.perform(post("/internal/batch/jobs/{job}/executions", JOB)
                        .header("X-Internal-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(bucket)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.job_name").value(JOB))
                .andExpect(jsonPath("$.data.bucket").value(bucket))
                .andExpect(jsonPath("$.data.outcome").value("STARTED"))
                .andExpect(jsonPath("$.data.execution_id").isNumber())
                .andReturn();

        Integer instances = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM BATCH_JOB_INSTANCE i
                JOIN BATCH_JOB_EXECUTION e ON e.JOB_INSTANCE_ID = i.JOB_INSTANCE_ID
                WHERE i.JOB_NAME = ? AND e.JOB_EXECUTION_ID = ?""", Integer.class, JOB, executionIdOf(result));
        assertThat(instances).isEqualTo(1);
    }

    @Test
    @DisplayName("시나리오 2: 이미 완료된 버킷으로 다시 호출하면 200·ALREADY_COMPLETED·같은 executionId 이고 BATCH 행은 늘지 않는다")
    void givenCompletedBucket_whenStartAgain_then200SameExecutionNoNewRows() throws Exception {
        String bucket = uniqueBucket();
        long first = executionIdOf(start(JOB, bucket));
        int before = instanceCount(bucket);

        MvcResult second = mockMvc.perform(post("/internal/batch/jobs/{job}/executions", JOB)
                        .header("X-Internal-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(bucket)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("ALREADY_COMPLETED"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andReturn();

        assertThat(executionIdOf(second)).isEqualTo(first);
        assertThat(instanceCount(bucket)).isEqualTo(before);
    }

    @Test
    @DisplayName("시나리오 3: 이전 실행이 FAILED 인 버킷은 같은 파라미터로 재시작되어 202·RESTARTED 와 새 executionId 를 받는다")
    void givenFailedBucket_whenStartAgain_then202Restarted() throws Exception {
        String bucket = uniqueBucket();
        MvcResult failed = mockMvc.perform(post("/internal/batch/jobs/{job}/executions", "internalApiFlakyJob")
                        .header("X-Internal-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(bucket)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andReturn();
        long failedId = executionIdOf(failed);

        MvcResult restarted = mockMvc.perform(post("/internal/batch/jobs/{job}/executions", "internalApiFlakyJob")
                        .header("X-Internal-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(bucket)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.outcome").value("RESTARTED"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andReturn();

        assertThat(executionIdOf(restarted)).isNotEqualTo(failedId);
        // 같은 JobInstance 아래 JobExecution 2개(FAILED → COMPLETED)
        Integer executions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM BATCH_JOB_EXECUTION
                WHERE JOB_INSTANCE_ID = (SELECT JOB_INSTANCE_ID FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ?)""",
                Integer.class, failedId);
        assertThat(executions).isEqualTo(2);
    }

    @Test
    @DisplayName("시나리오 5: 실행 ID 로 조회하면 status·exit_code·시각·스텝 카운터가 반환된다")
    void givenExecution_whenGet_thenStatusAndStepCounters() throws Exception {
        String bucket = uniqueBucket();
        long id = executionIdOf(start(JOB, bucket));

        mockMvc.perform(get("/internal/batch/executions/{id}", id)
                        .header("X-Internal-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.execution_id").value(id))
                .andExpect(jsonPath("$.data.job_name").value(JOB))
                .andExpect(jsonPath("$.data.bucket").value(bucket))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.exit_code").value("COMPLETED"))
                .andExpect(jsonPath("$.data.start_time").isNotEmpty())
                .andExpect(jsonPath("$.data.end_time").isNotEmpty())
                .andExpect(jsonPath("$.data.steps[0].name").value("notificationCleanupStep"))
                .andExpect(jsonPath("$.data.steps[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.steps[0].write_count").isNumber())
                .andExpect(jsonPath("$.data.steps[0].rollback_count").value(0));
    }

    @Test
    @DisplayName("시나리오 5-b: 없는 실행 ID 조회는 404 BATCH_JOB_EXECUTION_NOT_FOUND")
    void givenMissingExecution_whenGet_then404() throws Exception {
        mockMvc.perform(get("/internal/batch/executions/{id}", 999_999_999L)
                        .header("X-Internal-Token", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BATCH_JOB_EXECUTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("시나리오 6: 토큰이 없거나 틀리면 401 이고 잡은 시작되지 않는다")
    void givenNoOrWrongToken_whenCall_then401AndNoRun() throws Exception {
        String bucket = uniqueBucket();

        mockMvc.perform(post("/internal/batch/jobs/{job}/executions", JOB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(bucket)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/internal/batch/jobs/{job}/executions", JOB)
                        .header("X-Internal-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(bucket)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/internal/batch/executions/{id}", 1L))
                .andExpect(status().isUnauthorized());

        assertThat(instanceCount(bucket)).isZero();
    }

    @Test
    @DisplayName("시나리오 6-b: 유저 JWT 는 내부 경로 인증 수단이 아니다 — ACTIVE 유저의 진짜 유효한 토큰만 붙여도 401")
    void givenOnlyValidUserBearer_whenCall_then401() throws Exception {
        Long activeUserId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        String validBearer = jwtProvider.generateAccessToken(activeUserId);

        mockMvc.perform(get("/internal/batch/executions/{id}", 1L)
                        .header("Authorization", "Bearer " + validBearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("시나리오 6-c: 유효한 내부 토큰 + 차단 유저의 Bearer 가 함께 오면 내부 체인만 적용되어 202 다 (JWT 필터가 /internal 에 개입하지 않는다)")
    void givenInternalTokenAndBannedUserBearer_whenStart_then202NotUserForbidden() throws Exception {
        Long bannedUserId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.BANNED, null))).toDomain().id();
        String bannedBearer = jwtProvider.generateAccessToken(bannedUserId);

        mockMvc.perform(post("/internal/batch/jobs/{job}/executions", JOB)
                        .header("X-Internal-Token", token)
                        .header("Authorization", "Bearer " + bannedBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueBucket())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.outcome").value("STARTED"));
    }

    @Test
    @DisplayName("jobName 경로변수가 허용 문자 밖이면 400 (bucket 과 같은 화이트리스트 원칙) — 방화벽이 아닌 @Pattern 이 잡는 문자(.)로 검증")
    void givenInvalidJobName_whenStart_then400() throws Exception {
        // ';' 나 '%' 는 StrictHttpFirewall 이 먼저 거부해 @Pattern 의 효과를 증명하지 못한다. '.' 은 방화벽 통과, 패턴 거부.
        mockMvc.perform(post("/internal/batch/jobs/{job}/executions", "job.name")
                        .header("X-Internal-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueBucket())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("시나리오 7: 등록되지 않은 잡 이름이면 404 BATCH_JOB_NOT_FOUND")
    void givenUnknownJob_whenStart_then404() throws Exception {
        mockMvc.perform(post("/internal/batch/jobs/{job}/executions", "unknownJob")
                        .header("X-Internal-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueBucket())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BATCH_JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("bucket 이 비어 있거나 허용 문자 밖이면 400 이고 잡은 시작되지 않는다")
    void givenInvalidBucket_whenStart_then400() throws Exception {
        mockMvc.perform(post("/internal/batch/jobs/{job}/executions", JOB)
                        .header("X-Internal-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucket\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/internal/batch/jobs/{job}/executions", JOB)
                        .header("X-Internal-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bucket\":\"2026-09-04; DROP TABLE x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("스케줄러 토글 기본값(true)에서는 알림 정리 @Scheduled 런처 빈이 존재한다")
    void givenDefaultToggle_whenContextLoads_thenLauncherBeanExists() {
        assertThat(applicationContext.getBeanNamesForType(NotificationCleanupJobLauncher.class)).hasSize(1);
    }

    /** 첫 실행은 실패, 두 번째는 성공하는 잡 — FAILED 재시작 시나리오 전용. */
    @TestConfiguration
    static class FlakyJobConfig {

        private final AtomicInteger attempts = new AtomicInteger();

        @Bean
        Job internalApiFlakyJob(JobRepository jobRepository, Step internalApiFlakyStep) {
            return new JobBuilder("internalApiFlakyJob", jobRepository).start(internalApiFlakyStep).build();
        }

        @Bean
        Step internalApiFlakyStep(JobRepository jobRepository, PlatformTransactionManager tx) {
            Tasklet tasklet = (contribution, chunkContext) -> {
                if (attempts.getAndIncrement() % 2 == 0) {
                    throw new IllegalStateException("첫 시도 실패 (테스트용)");
                }
                return RepeatStatus.FINISHED;
            };
            return new StepBuilder("internalApiFlakyStep", jobRepository).tasklet(tasklet, tx).build();
        }
    }
}
